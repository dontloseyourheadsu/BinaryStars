using Microsoft.Extensions.Logging;
using System.Security.Claims;
using System.Text;
using BinaryStars.Api.Models;
using BinaryStars.Api.Services;
using BinaryStars.Application.Services.Transfers;
using BinaryStars.Application.Databases.Repositories.Transfers;
using BinaryStars.Domain.Transfers;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;

namespace BinaryStars.Api.Controllers;

/// <summary>
/// Provides file transfer endpoints backed by Kafka packet streaming.
/// </summary>
[ApiController]
[Route("api/files/transfers")]
[Authorize]
public class FileTransfersController : ControllerBase
{
    private readonly ILogger<FileTransfersController> _logger;

    private readonly IFileTransfersReadService _readService;
    private readonly IFileTransfersWriteService _writeService;
    private readonly IFileTransferRepository _repository;
    private readonly FileTransferKafkaService _kafkaService;
    private readonly FileTransferSettings _settings;
    private readonly KafkaSettings _kafkaSettings;

    /// <summary>
    /// Initializes a new instance of the <see cref="FileTransfersController"/> class.
    /// </summary>
    /// <param name="readService">The transfer read service.</param>
    /// <param name="writeService">The transfer write service.</param>
    /// <param name="repository">The transfer repository.</param>
    /// <param name="accountRepository">The account repository.</param>
    /// <param name="kafkaService">The Kafka transfer service.</param>
    /// <param name="settings">The file transfer settings.</param>
    /// <param name="kafkaSettings">The Kafka settings.</param>
    public FileTransfersController(
        IFileTransfersReadService readService,
        IFileTransfersWriteService writeService,
        IFileTransferRepository repository,
        FileTransferKafkaService kafkaService,
        IOptions<FileTransferSettings> settings,
        IOptions<KafkaSettings> kafkaSettings, ILogger<FileTransfersController> logger)
    {
        _logger = logger;

        _readService = readService;
        _writeService = writeService;
        _repository = repository;
        _kafkaService = kafkaService;
        _settings = settings.Value;
        _kafkaSettings = kafkaSettings.Value;
    }

    /// <summary>
    /// Gets all transfers for the authenticated user.
    /// </summary>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The list of transfers.</returns>
    [HttpGet]
    public async Task<IActionResult> GetTransfers([FromQuery] string? deviceId, CancellationToken cancellationToken)
    {
        var userId = GetUserId();
        var result = await _readService.GetTransfersByUserAsync(userId, cancellationToken);
        if (!result.IsSuccess)
            return BadRequest(result.Errors);

        _logger.LogInformation("GetTransfers: userId={UserId}, deviceId={DeviceId}, count={Count}", userId, deviceId, result.Value.Count);

        var projected = result.Value
            .Select(transfer =>
            {
                var isSender = !string.IsNullOrWhiteSpace(deviceId) &&
                               transfer.SenderDeviceId.Equals(deviceId, StringComparison.OrdinalIgnoreCase);
                
                // Debug log to help identify why a device might not see itself as the sender
                if (!string.IsNullOrWhiteSpace(deviceId) && transfer.SenderDeviceId.Contains(deviceId, StringComparison.OrdinalIgnoreCase))
                {
                     _logger.LogDebug("Transfer {TransferId} SenderDeviceId={Stored} match DeviceId={Passed}: {Match}", transfer.Id, transfer.SenderDeviceId, deviceId, isSender);
                }

                return transfer with { IsSender = isSender };
            })
            .ToList();

        return Ok(projected);
    }

    /// <summary>
    /// Gets pending transfers for a specific device.
    /// </summary>
    /// <param name="deviceId">The target device identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The list of pending transfers.</returns>
    [HttpGet("pending")]
    public async Task<IActionResult> GetPendingTransfers([FromQuery] string deviceId, CancellationToken cancellationToken)
    {
        var userId = GetUserId();
        var result = await _readService.GetPendingForDeviceAsync(userId, deviceId, cancellationToken);
        if (!result.IsSuccess)
            return BadRequest(result.Errors);

        // Pending transfers for a specific device are always ones it needs to receive.
        var projected = result.Value
            .Select(transfer => transfer with
            {
                IsSender = transfer.SenderDeviceId.Equals(deviceId, StringComparison.OrdinalIgnoreCase)
            })
            .ToList();

        return Ok(projected);
    }

    /// <summary>
    /// Gets a specific transfer by identifier.
    /// </summary>
    /// <param name="transferId">The transfer identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The transfer details.</returns>
    [HttpGet("{transferId:guid}")]
    public async Task<IActionResult> GetTransfer(Guid transferId, CancellationToken cancellationToken)
    {
        var userId = GetUserId();
        var result = await _readService.GetTransferAsync(userId, transferId, cancellationToken);
        if (!result.IsSuccess)
            return BadRequest(result.Errors);

        return Ok(result.Value);
    }

    /// <summary>
    /// Creates a new transfer and returns metadata for upload.
    /// </summary>
    /// <param name="request">The transfer creation request.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The created transfer detail response.</returns>
    [HttpPost]
    public async Task<IActionResult> CreateTransfer([FromBody] CreateFileTransferRequestDto request, CancellationToken cancellationToken)
    {
        var userId = GetUserId();
        if (request.SizeBytes <= 0)
            return BadRequest(new[] { "File size must be greater than zero." });

        const KafkaAuthMode authMode = KafkaAuthMode.Scram;
        var createRequest = new CreateFileTransferRequest(
            request.FileName,
            request.ContentType,
            request.SizeBytes,
            request.SenderDeviceId,
            request.TargetDeviceId,
            request.EncryptionEnvelope);

        var result = await _writeService.CreateTransferAsync(
            userId,
            createRequest,
            _kafkaSettings.Topic,
            authMode.ToString(),
            _settings.ChunkSizeBytes,
            _settings.ExpiresInMinutes,
            cancellationToken);

        if (!result.IsSuccess)
            return BadRequest(result.Errors);

        return CreatedAtAction(nameof(GetTransfer), new { transferId = result.Value.Id }, result.Value);
    }

    /// <summary>
    /// Uploads transfer bytes and stores them for direct download.
    /// </summary>
    /// <param name="transferId">The transfer identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>Ok when upload is ready for download.</returns>
    [HttpPut("{transferId:guid}/upload")]
    [DisableRequestSizeLimit]
    public async Task<IActionResult> UploadTransfer(Guid transferId, CancellationToken cancellationToken)
    {
        var userId = GetUserId();
        var transferResult = await _readService.GetTransferAsync(userId, transferId, cancellationToken);
        if (!transferResult.IsSuccess)
            return BadRequest(transferResult.Errors);

        var transfer = transferResult.Value;
        if (transfer.Status != FileTransferStatus.Queued && transfer.Status != FileTransferStatus.Failed)
            return BadRequest(new[] { "Transfer is not ready for upload." });

        if (transfer.ExpiresAt <= DateTimeOffset.UtcNow)
        {
            await _writeService.UpdateStatusAsync(transferId, FileTransferStatus.Expired, "Expired", null, cancellationToken);
            return BadRequest(new[] { "Transfer expired." });
        }

        var storedFilePath = GetStoredFilePath(transferId);
        var storeDirectory = Path.GetDirectoryName(storedFilePath);
        if (!string.IsNullOrWhiteSpace(storeDirectory))
        {
            Directory.CreateDirectory(storeDirectory);
        }

        await _writeService.UpdateStatusAsync(transferId, FileTransferStatus.Uploading, null, null, cancellationToken);

        _logger.LogInformation("UploadTransfer: Receiving bytes for transfer {TransferId}. Expected size: {ExpectedSize}", transferId, transfer.SizeBytes);

        try
        {
            await using (var targetStream = System.IO.File.Create(storedFilePath))
            {
                await Request.Body.CopyToAsync(targetStream, cancellationToken);
            }

            var storedInfo = new FileInfo(storedFilePath);
            _logger.LogInformation("UploadTransfer: Completed receive for {TransferId}. Actual size: {ActualSize}", transferId, storedInfo.Length);

            if (storedInfo.Length != transfer.SizeBytes)
            {
                _logger.LogWarning("UploadTransfer: Size mismatch for {TransferId}. Expected {Expected}, got {Actual}. Deleting partial file.", transferId, transfer.SizeBytes, storedInfo.Length);
                TryDeleteStoredFile(storedFilePath);
                await _writeService.UpdateStatusAsync(transferId, FileTransferStatus.Failed, "Uploaded size does not match transfer metadata.", null, cancellationToken);
                return BadRequest(new[] { $"Uploaded file size ({storedInfo.Length}) does not match transfer metadata ({transfer.SizeBytes})." });
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "UploadTransfer: Error during file write for {TransferId}. Cleaning up.", transferId);
            TryDeleteStoredFile(storedFilePath);
            throw;
        }

        await _writeService.UpdateStatusAsync(transferId, FileTransferStatus.Available, null, null, cancellationToken);
        return Ok(new { transferId });
    }

    /// <summary>
    /// Streams transfer bytes to the client.
    /// </summary>
    /// <param name="transferId">The transfer identifier.</param>
    /// <param name="deviceId">The target device identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>File stream response.</returns>
    [HttpGet("{transferId:guid}/download")]
    public async Task<IActionResult> DownloadTransfer(Guid transferId, [FromQuery] string deviceId, CancellationToken cancellationToken)
    {
        var userId = GetUserId();
        var transferResult = await _readService.GetTransferAsync(userId, transferId, cancellationToken);
        if (!transferResult.IsSuccess)
            return BadRequest(transferResult.Errors);

        var transferDetail = transferResult.Value;
        if (!transferDetail.TargetDeviceId.Equals(deviceId, StringComparison.OrdinalIgnoreCase))
        {
            _logger.LogWarning("DownloadTransfer 400: Device {DeviceId} attempted to download transfer {TransferId} targeting {TargetDeviceId}", deviceId, transferId, transferDetail.TargetDeviceId);
            return BadRequest(new[] { $"Transfer does not target this device. (Target: {transferDetail.TargetDeviceId})" });
        }

        var canDownload = transferDetail.Status == FileTransferStatus.Available ||
                          transferDetail.Status == FileTransferStatus.Queued ||
                          transferDetail.Status == FileTransferStatus.Uploading;

        var storedFilePath = GetStoredFilePath(transferId);
        if (!canDownload && transferDetail.Status == FileTransferStatus.Failed && System.IO.File.Exists(storedFilePath))
        {
            _logger.LogInformation("DownloadTransfer: Allowing download of FAILED transfer {TransferId} because file exists on disk.", transferId);
            canDownload = true;
        }

        if (!canDownload)
        {
            _logger.LogWarning("DownloadTransfer 400: Transfer {TransferId} is in status {Status}, not ready for download.", transferId, transferDetail.Status);
            return BadRequest(new[] { $"Transfer is not ready for download. Current status: {transferDetail.Status}" });
        }

        if (transferDetail.ExpiresAt <= DateTimeOffset.UtcNow)
        {
            _logger.LogWarning("DownloadTransfer 400: Transfer {TransferId} expired at {ExpiresAt}", transferId, transferDetail.ExpiresAt);
            await _writeService.UpdateStatusAsync(transferId, FileTransferStatus.Expired, "Expired", null, cancellationToken);
            return BadRequest(new[] { "Transfer expired." });
        }

        var transfer = await _repository.GetByIdAsync(transferId, cancellationToken);
        if (transfer == null)
            return NotFound();

        Response.ContentType = transfer.ContentType;
        Response.Headers["Content-Disposition"] = $"attachment; filename=\"{transfer.FileName}\"";
        if (!string.IsNullOrWhiteSpace(transfer.EncryptionEnvelope))
        {
            var envelopeBytes = Encoding.UTF8.GetBytes(transfer.EncryptionEnvelope);
            Response.Headers["X-Transfer-Envelope"] = Convert.ToBase64String(envelopeBytes);
        }
        Response.Headers["X-Transfer-ChunkSize"] = transfer.ChunkSizeBytes.ToString();

        try
        {
            if (System.IO.File.Exists(storedFilePath))
            {
                await using var sourceStream = System.IO.File.OpenRead(storedFilePath);
                await sourceStream.CopyToAsync(Response.Body, cancellationToken);
                _logger.LogInformation("Successfully streamed transfer {TransferId} from disk.", transferId);
            }
            else
            {
                _logger.LogInformation("Transfer {TransferId} not found on disk, attempting Kafka stream.", transferId);
                const KafkaAuthMode authMode = KafkaAuthMode.Scram;
                await _kafkaService.StreamToAsync(transfer, Response.Body, authMode, null, cancellationToken);
                await _kafkaService.DeleteTransferPacketsAsync(transfer, authMode, null, cancellationToken);
            }

            // We no longer delete the stored file immediately to handle retries and multiple downloads.
            // TryDeleteStoredFile(storedFilePath);
            
            await _writeService.UpdateStatusAsync(transferId, FileTransferStatus.Downloaded, null, DateTimeOffset.UtcNow, cancellationToken);
            return new EmptyResult();
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to stream transfer {TransferId}.", transferId);
            // We don't mark as failed here to allow retry if it was a transient/connection issue.
            return StatusCode(500, new[] { "Failed to stream transfer." });
        }
    }

    /// <summary>
    /// Rejects a transfer and deletes pending packets.
    /// </summary>
    /// <param name="transferId">The transfer identifier.</param>
    /// <param name="deviceId">The target device identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>Ok on success.</returns>
    [HttpPost("{transferId:guid}/reject")]
    public async Task<IActionResult> RejectTransfer(Guid transferId, [FromQuery] string deviceId, CancellationToken cancellationToken)
    {
        var userId = GetUserId();
        var transferResult = await _readService.GetTransferAsync(userId, transferId, cancellationToken);
        if (!transferResult.IsSuccess)
            return BadRequest(transferResult.Errors);

        var transferDetail = transferResult.Value;
        if (!transferDetail.TargetDeviceId.Equals(deviceId, StringComparison.OrdinalIgnoreCase))
            return BadRequest(new[] { "Transfer does not target this device." });

        var transfer = await _repository.GetByIdAsync(transferId, cancellationToken);
        if (transfer == null)
            return NotFound();

        TryDeleteStoredFile(GetStoredFilePath(transferId));
        if (transfer.PacketCount > 0)
        {
            const KafkaAuthMode authMode = KafkaAuthMode.Scram;
            await _kafkaService.DeleteTransferPacketsAsync(transfer, authMode, null, cancellationToken);
        }
        await _writeService.UpdateStatusAsync(transferId, FileTransferStatus.Rejected, "Rejected", DateTimeOffset.UtcNow, cancellationToken);

        return Ok();
    }

    /// <summary>
    /// Clears transfers for the current device by scope.
    /// </summary>
    [HttpPost("clear")]
    public async Task<IActionResult> ClearTransfers([FromBody] ClearFileTransfersRequestDto request, CancellationToken cancellationToken)
    {
        var userId = GetUserId();
        _logger.LogInformation("ClearTransfers requested for DeviceId: {DeviceId}, Scope: {Scope}, UserId: {UserId}", request.DeviceId, request.Scope, userId);

        if (string.IsNullOrWhiteSpace(request.DeviceId))
            return BadRequest(new[] { "DeviceId is required." });

        var scope = (request.Scope ?? "all").Trim().ToLowerInvariant();
        if (scope is not ("all" or "sent" or "received"))
            return BadRequest(new[] { "Scope must be one of: all, sent, received." });

        var allResult = await _readService.GetTransfersByUserAsync(userId, cancellationToken);
        if (!allResult.IsSuccess)
            return BadRequest(allResult.Errors);

        var toDelete = allResult.Value
            .Where(transfer =>
                scope == "all" &&
                (transfer.SenderDeviceId.Equals(request.DeviceId, StringComparison.OrdinalIgnoreCase) ||
                 transfer.TargetDeviceId.Equals(request.DeviceId, StringComparison.OrdinalIgnoreCase)) ||
                scope == "sent" && transfer.SenderDeviceId.Equals(request.DeviceId, StringComparison.OrdinalIgnoreCase) ||
                scope == "received" && transfer.TargetDeviceId.Equals(request.DeviceId, StringComparison.OrdinalIgnoreCase))
            .Select(transfer => transfer.Id)
            .Distinct()
            .ToList();

        _logger.LogInformation("Found {Count} transfers to delete for device {DeviceId} in scope {Scope}.", toDelete.Count, request.DeviceId, scope);

        var deleted = 0;
        foreach (var transferId in toDelete)
        {
            var transfer = await _repository.GetByIdAsync(transferId, cancellationToken);
            if (transfer == null)
                continue;

            TryDeleteStoredFile(GetStoredFilePath(transferId));
            if (transfer.PacketCount > 0)
            {
                const KafkaAuthMode authMode = KafkaAuthMode.Scram;
                await _kafkaService.DeleteTransferPacketsAsync(transfer, authMode, null, cancellationToken);
            }

            await _repository.DeleteAsync(transfer, cancellationToken);
            deleted++;
        }

        if (deleted > 0)
            await _repository.SaveChangesAsync(cancellationToken);

        return Ok(new { deleted });
    }

    private Guid GetUserId()
    {
        return Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
    }

    private string GetStoredFilePath(Guid transferId)
    {
        return Path.Combine(_settings.TempPath, "store", $"{transferId:D}.bin");
    }

    private static void TryDeleteStoredFile(string path)
    {
        try
        {
            if (System.IO.File.Exists(path))
            {
                System.IO.File.Delete(path);
            }
        }
        catch
        {
            // Best-effort cleanup.
        }
    }
}

/// <summary>
/// Request payload for creating a file transfer.
/// </summary>
/// <param name="FileName">The original file name.</param>
/// <param name="ContentType">The MIME content type.</param>
/// <param name="SizeBytes">The file size in bytes.</param>
/// <param name="SenderDeviceId">The sender device identifier.</param>
/// <param name="TargetDeviceId">The target device identifier.</param>
/// <param name="EncryptionEnvelope">The encryption envelope metadata.</param>
public record CreateFileTransferRequestDto(
    string FileName,
    string ContentType,
    long SizeBytes,
    string SenderDeviceId,
    string TargetDeviceId,
    string EncryptionEnvelope);

/// <summary>
/// Request payload for clearing transfers by scope.
/// </summary>
public record ClearFileTransfersRequestDto(
    string DeviceId,
    string Scope);
