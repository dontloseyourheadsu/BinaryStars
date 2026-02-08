using System.Security.Claims;
using System.Text;
using BinaryStars.Api.Models;
using BinaryStars.Api.Services;
using BinaryStars.Application.Databases.Repositories.Accounts;
using BinaryStars.Application.Databases.Repositories.Transfers;
using BinaryStars.Application.Services.Transfers;
using BinaryStars.Domain.Transfers;
using Hangfire;
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
    private readonly IFileTransfersReadService _readService;
    private readonly IFileTransfersWriteService _writeService;
    private readonly IFileTransferRepository _repository;
    private readonly IAccountRepository _accountRepository;
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
        IAccountRepository accountRepository,
        FileTransferKafkaService kafkaService,
        IOptions<FileTransferSettings> settings,
        IOptions<KafkaSettings> kafkaSettings)
    {
        _readService = readService;
        _writeService = writeService;
        _repository = repository;
        _accountRepository = accountRepository;
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
    public async Task<IActionResult> GetTransfers(CancellationToken cancellationToken)
    {
        var userId = GetUserId();
        var result = await _readService.GetTransfersByUserAsync(userId, cancellationToken);
        if (!result.IsSuccess)
            return BadRequest(result.Errors);

        return Ok(result.Value);
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

        return Ok(result.Value);
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

        var authMode = await ResolveKafkaAuthModeAsync(userId);
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
    /// Uploads transfer bytes and queues Kafka publishing.
    /// </summary>
    /// <param name="transferId">The transfer identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>Accepted when queued for processing.</returns>
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

        Directory.CreateDirectory(_settings.TempPath);
        var tempFilePath = Path.Combine(_settings.TempPath, $"{transferId:D}.bin");

        await using (var targetStream = System.IO.File.Create(tempFilePath))
        {
            await Request.Body.CopyToAsync(targetStream, cancellationToken);
        }

        var authMode = ParseAuthMode(transfer.KafkaAuthMode);
        var oauthToken = authMode == KafkaAuthMode.OauthBearer ? ExtractBearerToken() : null;

        BackgroundJob.Enqueue<FileTransferJob>(job => job.SendToKafkaAsync(
            new FileTransferJobRequest(transferId, tempFilePath, transfer.KafkaAuthMode, oauthToken)));

        return Accepted(new { transferId });
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
            return BadRequest(new[] { "Transfer does not target this device." });

        if (transferDetail.Status != FileTransferStatus.Available)
            return BadRequest(new[] { "Transfer is not available." });

        if (transferDetail.ExpiresAt <= DateTimeOffset.UtcNow)
        {
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

        var authMode = ParseAuthMode(transfer.KafkaAuthMode);
        var oauthToken = authMode == KafkaAuthMode.OauthBearer ? ExtractBearerToken() : null;

        try
        {
            await _kafkaService.StreamToAsync(transfer, Response.Body, authMode, oauthToken, cancellationToken);
            await _kafkaService.DeleteTransferPacketsAsync(transfer, KafkaAuthMode.Scram, null, cancellationToken);
            await _writeService.UpdateStatusAsync(transferId, FileTransferStatus.Downloaded, null, DateTimeOffset.UtcNow, cancellationToken);
            return new EmptyResult();
        }
        catch (Exception ex)
        {
            await _writeService.UpdateStatusAsync(transferId, FileTransferStatus.Failed, ex.Message, null, cancellationToken);
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

        await _kafkaService.DeleteTransferPacketsAsync(transfer, KafkaAuthMode.Scram, null, cancellationToken);
        await _writeService.UpdateStatusAsync(transferId, FileTransferStatus.Rejected, "Rejected", DateTimeOffset.UtcNow, cancellationToken);

        return Ok();
    }

    private Guid GetUserId()
    {
        return Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
    }

    private async Task<KafkaAuthMode> ResolveKafkaAuthModeAsync(Guid userId)
    {
        var user = await _accountRepository.FindByIdAsync(userId);
        if (user == null)
            return KafkaAuthMode.Scram;

        var logins = await _accountRepository.GetLoginsAsync(user);
        return logins.Any() ? KafkaAuthMode.OauthBearer : KafkaAuthMode.Scram;
    }

    private KafkaAuthMode ParseAuthMode(string mode)
    {
        return Enum.TryParse<KafkaAuthMode>(mode, true, out var parsed) ? parsed : KafkaAuthMode.Scram;
    }

    private string? ExtractBearerToken()
    {
        var header = Request.Headers.Authorization.ToString();
        if (header.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
            return header.Substring("Bearer ".Length).Trim();

        return null;
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
