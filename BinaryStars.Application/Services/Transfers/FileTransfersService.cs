using BinaryStars.Application.Databases.DatabaseModels.Transfers;
using BinaryStars.Application.Databases.Repositories.Devices;
using BinaryStars.Application.Databases.Repositories.Transfers;
using BinaryStars.Domain;
using BinaryStars.Domain.Errors.Transfers;
using BinaryStars.Domain.Transfers;

namespace BinaryStars.Application.Services.Transfers;

/// <summary>
/// Request payload for creating a new file transfer.
/// </summary>
/// <param name="FileName">The original file name.</param>
/// <param name="ContentType">The MIME content type.</param>
/// <param name="SizeBytes">The size of the file in bytes.</param>
/// <param name="SenderDeviceId">The sender device identifier.</param>
/// <param name="TargetDeviceId">The target device identifier.</param>
/// <param name="EncryptionEnvelope">The encryption envelope metadata.</param>
public record CreateFileTransferRequest(
    string FileName,
    string ContentType,
    long SizeBytes,
    string SenderDeviceId,
    string TargetDeviceId,
    string EncryptionEnvelope);

/// <summary>
/// Summary payload for transfers listed to clients.
/// </summary>
/// <param name="Id">The transfer identifier.</param>
/// <param name="FileName">The original file name.</param>
/// <param name="ContentType">The MIME content type.</param>
/// <param name="SizeBytes">The file size in bytes.</param>
/// <param name="SenderDeviceId">The sender device identifier.</param>
/// <param name="TargetDeviceId">The target device identifier.</param>
/// <param name="Status">The current transfer status.</param>
/// <param name="CreatedAt">The creation timestamp.</param>
/// <param name="ExpiresAt">The expiration timestamp.</param>
/// <param name="IsSender">Whether the current user is the sender.</param>
public record FileTransferSummaryResponse(
    Guid Id,
    string FileName,
    string ContentType,
    long SizeBytes,
    string SenderDeviceId,
    string TargetDeviceId,
    FileTransferStatus Status,
    DateTimeOffset CreatedAt,
    DateTimeOffset ExpiresAt,
    bool IsSender);

/// <summary>
/// Detailed payload for a specific transfer.
/// </summary>
/// <param name="Id">The transfer identifier.</param>
/// <param name="FileName">The original file name.</param>
/// <param name="ContentType">The MIME content type.</param>
/// <param name="SizeBytes">The file size in bytes.</param>
/// <param name="SenderUserId">The sender user identifier.</param>
/// <param name="TargetUserId">The target user identifier.</param>
/// <param name="SenderDeviceId">The sender device identifier.</param>
/// <param name="TargetDeviceId">The target device identifier.</param>
/// <param name="Status">The current transfer status.</param>
/// <param name="EncryptionEnvelope">The encryption envelope metadata.</param>
/// <param name="ChunkSizeBytes">The size of each transfer chunk.</param>
/// <param name="PacketCount">The number of packets associated with the transfer.</param>
/// <param name="KafkaTopic">The Kafka topic used for the transfer.</param>
/// <param name="KafkaPartition">The Kafka partition used for the transfer.</param>
/// <param name="KafkaStartOffset">The Kafka start offset.</param>
/// <param name="KafkaEndOffset">The Kafka end offset.</param>
/// <param name="KafkaAuthMode">The Kafka authentication mode.</param>
/// <param name="CreatedAt">The creation timestamp.</param>
/// <param name="ExpiresAt">The expiration timestamp.</param>
/// <param name="CompletedAt">The completion timestamp, if any.</param>
public record FileTransferDetailResponse(
    Guid Id,
    string FileName,
    string ContentType,
    long SizeBytes,
    Guid SenderUserId,
    Guid TargetUserId,
    string SenderDeviceId,
    string TargetDeviceId,
    FileTransferStatus Status,
    string? EncryptionEnvelope,
    int ChunkSizeBytes,
    int PacketCount,
    string KafkaTopic,
    int? KafkaPartition,
    long? KafkaStartOffset,
    long? KafkaEndOffset,
    string KafkaAuthMode,
    DateTimeOffset CreatedAt,
    DateTimeOffset ExpiresAt,
    DateTimeOffset? CompletedAt);

/// <summary>
/// Read-only file transfer operations.
/// </summary>
public interface IFileTransfersReadService
{
    /// <summary>
    /// Gets all transfers for a user.
    /// </summary>
    /// <param name="userId">The user identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The list of transfers or a failure result.</returns>
    Task<Result<List<FileTransferSummaryResponse>>> GetTransfersByUserAsync(Guid userId, CancellationToken cancellationToken);

    /// <summary>
    /// Gets pending transfers for a specific device.
    /// </summary>
    /// <param name="userId">The user identifier.</param>
    /// <param name="deviceId">The device identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The list of transfers or a failure result.</returns>
    Task<Result<List<FileTransferSummaryResponse>>> GetPendingForDeviceAsync(Guid userId, string deviceId, CancellationToken cancellationToken);

    /// <summary>
    /// Gets the details of a specific transfer.
    /// </summary>
    /// <param name="userId">The user identifier.</param>
    /// <param name="transferId">The transfer identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The transfer detail or a failure result.</returns>
    Task<Result<FileTransferDetailResponse>> GetTransferAsync(Guid userId, Guid transferId, CancellationToken cancellationToken);
}

/// <summary>
/// Write-only file transfer operations.
/// </summary>
public interface IFileTransfersWriteService
{
    /// <summary>
    /// Creates a new transfer and persists the Kafka metadata required for streaming.
    /// </summary>
    /// <param name="userId">The user identifier.</param>
    /// <param name="request">The transfer creation request.</param>
    /// <param name="kafkaTopic">The Kafka topic used for transfer packets.</param>
    /// <param name="kafkaAuthMode">The Kafka authentication mode.</param>
    /// <param name="chunkSizeBytes">The chunk size in bytes.</param>
    /// <param name="expiresInMinutes">The time-to-live in minutes.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The created transfer detail or a failure result.</returns>
    Task<Result<FileTransferDetailResponse>> CreateTransferAsync(
        Guid userId,
        CreateFileTransferRequest request,
        string kafkaTopic,
        string kafkaAuthMode,
        int chunkSizeBytes,
        int expiresInMinutes,
        CancellationToken cancellationToken);

    /// <summary>
    /// Updates the lifecycle status for an existing transfer.
    /// </summary>
    /// <param name="transferId">The transfer identifier.</param>
    /// <param name="status">The updated status.</param>
    /// <param name="failureReason">An optional failure reason.</param>
    /// <param name="completedAt">An optional completion timestamp.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>A success or failure result.</returns>
    Task<Result> UpdateStatusAsync(Guid transferId, FileTransferStatus status, string? failureReason, DateTimeOffset? completedAt, CancellationToken cancellationToken);

    /// <summary>
    /// Updates Kafka packet metadata for an existing transfer.
    /// </summary>
    /// <param name="transferId">The transfer identifier.</param>
    /// <param name="packetCount">The total packet count.</param>
    /// <param name="chunkSizeBytes">The chunk size in bytes.</param>
    /// <param name="partition">The Kafka partition.</param>
    /// <param name="startOffset">The start offset in Kafka.</param>
    /// <param name="endOffset">The end offset in Kafka.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>A success or failure result.</returns>
    Task<Result> UpdateKafkaInfoAsync(Guid transferId, int packetCount, int chunkSizeBytes, int? partition, long? startOffset, long? endOffset, CancellationToken cancellationToken);
}

/// <summary>
/// Application service for file transfer lifecycle and metadata.
/// </summary>
public class FileTransfersService : IFileTransfersReadService, IFileTransfersWriteService
{
    private readonly IFileTransferRepository _repository;
    private readonly IDeviceRepository _deviceRepository;
    public FileTransfersService(
        IFileTransferRepository repository,
        IDeviceRepository deviceRepository)
    {
        _repository = repository;
        _deviceRepository = deviceRepository;
    }

    /// <inheritdoc />
    public async Task<Result<FileTransferDetailResponse>> CreateTransferAsync(
        Guid userId,
        CreateFileTransferRequest request,
        string kafkaTopic,
        string kafkaAuthMode,
        int chunkSizeBytes,
        int expiresInMinutes,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.FileName))
            return Result<FileTransferDetailResponse>.Failure(FileTransferErrors.FileNameCannotBeNullOrWhitespace);

        if (string.IsNullOrWhiteSpace(request.ContentType))
            return Result<FileTransferDetailResponse>.Failure(FileTransferErrors.ContentTypeCannotBeNullOrWhitespace);

        if (request.SizeBytes <= 0)
            return Result<FileTransferDetailResponse>.Failure(FileTransferErrors.FileSizeMustBePositive);

        if (string.IsNullOrWhiteSpace(request.SenderDeviceId))
            return Result<FileTransferDetailResponse>.Failure(FileTransferErrors.SenderDeviceIdCannotBeNullOrWhitespace);

        if (string.IsNullOrWhiteSpace(request.TargetDeviceId))
            return Result<FileTransferDetailResponse>.Failure(FileTransferErrors.TargetDeviceIdCannotBeNullOrWhitespace);

        if (string.IsNullOrWhiteSpace(request.EncryptionEnvelope))
            return Result<FileTransferDetailResponse>.Failure(FileTransferErrors.EncryptionEnvelopeMissing);

        var senderDevice = await _deviceRepository.GetByIdAsync(request.SenderDeviceId, cancellationToken);
        if (senderDevice == null || senderDevice.UserId != userId)
            return Result<FileTransferDetailResponse>.Failure(FileTransferErrors.TransferNotOwnedByUser);

        var targetDevice = await _deviceRepository.GetByIdAsync(request.TargetDeviceId, cancellationToken);
        if (targetDevice == null || targetDevice.UserId != userId)
            return Result<FileTransferDetailResponse>.Failure(FileTransferErrors.TransferNotOwnedByUser);

        var now = DateTimeOffset.UtcNow;
        var transfer = new FileTransferDbModel
        {
            Id = Guid.NewGuid(),
            FileName = request.FileName,
            ContentType = request.ContentType,
            SizeBytes = request.SizeBytes,
            SenderUserId = userId,
            TargetUserId = targetDevice.UserId,
            SenderDeviceId = request.SenderDeviceId,
            TargetDeviceId = request.TargetDeviceId,
            Status = FileTransferStatus.Queued,
            EncryptionEnvelope = request.EncryptionEnvelope,
            ChunkSizeBytes = chunkSizeBytes,
            PacketCount = 0,
            KafkaTopic = kafkaTopic,
            KafkaAuthMode = kafkaAuthMode,
            CreatedAt = now,
            ExpiresAt = now.AddMinutes(expiresInMinutes)
        };

        await _repository.AddAsync(transfer, cancellationToken);
        await _repository.SaveChangesAsync(cancellationToken);

        return Result<FileTransferDetailResponse>.Success(ToDetailResponse(transfer));
    }

    /// <inheritdoc />
    public async Task<Result> UpdateStatusAsync(Guid transferId, FileTransferStatus status, string? failureReason, DateTimeOffset? completedAt, CancellationToken cancellationToken)
    {
        var transfer = await _repository.GetByIdAsync(transferId, cancellationToken);
        if (transfer == null)
            return Result.Failure(FileTransferErrors.TransferNotFound);

        transfer.Status = status;
        transfer.FailureReason = failureReason;
        transfer.CompletedAt = completedAt;
        await _repository.UpdateAsync(transfer, cancellationToken);
        await _repository.SaveChangesAsync(cancellationToken);

        return Result.Success();
    }

    /// <inheritdoc />
    public async Task<Result> UpdateKafkaInfoAsync(Guid transferId, int packetCount, int chunkSizeBytes, int? partition, long? startOffset, long? endOffset, CancellationToken cancellationToken)
    {
        var transfer = await _repository.GetByIdAsync(transferId, cancellationToken);
        if (transfer == null)
            return Result.Failure(FileTransferErrors.TransferNotFound);

        transfer.PacketCount = packetCount;
        transfer.ChunkSizeBytes = chunkSizeBytes;
        transfer.KafkaPartition = partition;
        transfer.KafkaStartOffset = startOffset;
        transfer.KafkaEndOffset = endOffset;
        await _repository.UpdateAsync(transfer, cancellationToken);
        await _repository.SaveChangesAsync(cancellationToken);

        return Result.Success();
    }

    /// <inheritdoc />
    public async Task<Result<List<FileTransferSummaryResponse>>> GetTransfersByUserAsync(Guid userId, CancellationToken cancellationToken)
    {
        var transfers = await _repository.GetByUserAsync(userId, cancellationToken);
        var responses = transfers.Select(t => ToSummaryResponse(t, t.SenderUserId == userId)).ToList();
        return Result<List<FileTransferSummaryResponse>>.Success(responses);
    }

    /// <inheritdoc />
    public async Task<Result<List<FileTransferSummaryResponse>>> GetPendingForDeviceAsync(Guid userId, string deviceId, CancellationToken cancellationToken)
    {
        var device = await _deviceRepository.GetByIdAsync(deviceId, cancellationToken);
        if (device == null || device.UserId != userId)
            return Result<List<FileTransferSummaryResponse>>.Failure(FileTransferErrors.TransferNotOwnedByUser);

        var transfers = await _repository.GetPendingByTargetDeviceIdAsync(deviceId, cancellationToken);
        var responses = transfers.Select(t => ToSummaryResponse(t, t.SenderUserId == userId)).ToList();
        return Result<List<FileTransferSummaryResponse>>.Success(responses);
    }

    /// <inheritdoc />
    public async Task<Result<FileTransferDetailResponse>> GetTransferAsync(Guid userId, Guid transferId, CancellationToken cancellationToken)
    {
        var transfer = await _repository.GetByIdAsync(transferId, cancellationToken);
        if (transfer == null)
            return Result<FileTransferDetailResponse>.Failure(FileTransferErrors.TransferNotFound);

        if (transfer.SenderUserId != userId && transfer.TargetUserId != userId)
            return Result<FileTransferDetailResponse>.Failure(FileTransferErrors.TransferNotOwnedByUser);

        return Result<FileTransferDetailResponse>.Success(ToDetailResponse(transfer));
    }

    private static FileTransferSummaryResponse ToSummaryResponse(FileTransferDbModel transfer, bool isSender)
    {
        return new FileTransferSummaryResponse(
            transfer.Id,
            transfer.FileName,
            transfer.ContentType,
            transfer.SizeBytes,
            transfer.SenderDeviceId,
            transfer.TargetDeviceId,
            transfer.Status,
            transfer.CreatedAt,
            transfer.ExpiresAt,
            isSender);
    }

    private static FileTransferDetailResponse ToDetailResponse(FileTransferDbModel transfer)
    {
        return new FileTransferDetailResponse(
            transfer.Id,
            transfer.FileName,
            transfer.ContentType,
            transfer.SizeBytes,
            transfer.SenderUserId,
            transfer.TargetUserId,
            transfer.SenderDeviceId,
            transfer.TargetDeviceId,
            transfer.Status,
            transfer.EncryptionEnvelope,
            transfer.ChunkSizeBytes,
            transfer.PacketCount,
            transfer.KafkaTopic,
            transfer.KafkaPartition,
            transfer.KafkaStartOffset,
            transfer.KafkaEndOffset,
            transfer.KafkaAuthMode,
            transfer.CreatedAt,
            transfer.ExpiresAt,
            transfer.CompletedAt);
    }
}
