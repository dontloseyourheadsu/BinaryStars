using BinaryStars.Application.Databases.DatabaseModels.Transfers;
using BinaryStars.Application.Databases.Repositories.Devices;
using BinaryStars.Application.Databases.Repositories.Transfers;
using BinaryStars.Domain;
using BinaryStars.Domain.Errors.Transfers;
using BinaryStars.Domain.Transfers;

namespace BinaryStars.Application.Services.Transfers;

public record CreateFileTransferRequest(
    string FileName,
    string ContentType,
    long SizeBytes,
    string SenderDeviceId,
    string TargetDeviceId,
    string EncryptionEnvelope);

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

public interface IFileTransfersReadService
{
    Task<Result<List<FileTransferSummaryResponse>>> GetTransfersByUserAsync(Guid userId, CancellationToken cancellationToken);
    Task<Result<List<FileTransferSummaryResponse>>> GetPendingForDeviceAsync(Guid userId, string deviceId, CancellationToken cancellationToken);
    Task<Result<FileTransferDetailResponse>> GetTransferAsync(Guid userId, Guid transferId, CancellationToken cancellationToken);
}

public interface IFileTransfersWriteService
{
    Task<Result<FileTransferDetailResponse>> CreateTransferAsync(
        Guid userId,
        CreateFileTransferRequest request,
        string kafkaTopic,
        string kafkaAuthMode,
        int chunkSizeBytes,
        int expiresInMinutes,
        CancellationToken cancellationToken);

    Task<Result> UpdateStatusAsync(Guid transferId, FileTransferStatus status, string? failureReason, DateTimeOffset? completedAt, CancellationToken cancellationToken);
    Task<Result> UpdateKafkaInfoAsync(Guid transferId, int packetCount, int chunkSizeBytes, int? partition, long? startOffset, long? endOffset, CancellationToken cancellationToken);
}

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

    public async Task<Result<List<FileTransferSummaryResponse>>> GetTransfersByUserAsync(Guid userId, CancellationToken cancellationToken)
    {
        var transfers = await _repository.GetByUserAsync(userId, cancellationToken);
        var responses = transfers.Select(t => ToSummaryResponse(t, t.SenderUserId == userId)).ToList();
        return Result<List<FileTransferSummaryResponse>>.Success(responses);
    }

    public async Task<Result<List<FileTransferSummaryResponse>>> GetPendingForDeviceAsync(Guid userId, string deviceId, CancellationToken cancellationToken)
    {
        var device = await _deviceRepository.GetByIdAsync(deviceId, cancellationToken);
        if (device == null || device.UserId != userId)
            return Result<List<FileTransferSummaryResponse>>.Failure(FileTransferErrors.TransferNotOwnedByUser);

        var transfers = await _repository.GetPendingByTargetDeviceIdAsync(deviceId, cancellationToken);
        var responses = transfers.Select(t => ToSummaryResponse(t, t.SenderUserId == userId)).ToList();
        return Result<List<FileTransferSummaryResponse>>.Success(responses);
    }

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
