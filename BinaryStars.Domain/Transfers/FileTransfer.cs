using BinaryStars.Domain.Errors.Transfers;

namespace BinaryStars.Domain.Transfers;

public readonly record struct FileTransfer
{
    public Guid Id { get; init; }
    public string FileName { get; init; }
    public string ContentType { get; init; }
    public long SizeBytes { get; init; }
    public Guid SenderUserId { get; init; }
    public Guid TargetUserId { get; init; }
    public string SenderDeviceId { get; init; }
    public string TargetDeviceId { get; init; }
    public FileTransferStatus Status { get; init; }
    public DateTimeOffset CreatedAt { get; init; }
    public DateTimeOffset ExpiresAt { get; init; }

    public FileTransfer(
        Guid id,
        string fileName,
        string contentType,
        long sizeBytes,
        Guid senderUserId,
        Guid targetUserId,
        string senderDeviceId,
        string targetDeviceId,
        FileTransferStatus status,
        DateTimeOffset createdAt,
        DateTimeOffset expiresAt)
    {
        if (id == Guid.Empty) throw new ArgumentException(FileTransferErrors.TransferIdCannotBeEmpty, nameof(id));
        if (string.IsNullOrWhiteSpace(fileName)) throw new ArgumentException(FileTransferErrors.FileNameCannotBeNullOrWhitespace, nameof(fileName));
        if (string.IsNullOrWhiteSpace(contentType)) throw new ArgumentException(FileTransferErrors.ContentTypeCannotBeNullOrWhitespace, nameof(contentType));
        if (sizeBytes <= 0) throw new ArgumentException(FileTransferErrors.FileSizeMustBePositive, nameof(sizeBytes));
        if (senderUserId == Guid.Empty) throw new ArgumentException(FileTransferErrors.SenderUserIdCannotBeEmpty, nameof(senderUserId));
        if (targetUserId == Guid.Empty) throw new ArgumentException(FileTransferErrors.TargetUserIdCannotBeEmpty, nameof(targetUserId));
        if (string.IsNullOrWhiteSpace(senderDeviceId)) throw new ArgumentException(FileTransferErrors.SenderDeviceIdCannotBeNullOrWhitespace, nameof(senderDeviceId));
        if (string.IsNullOrWhiteSpace(targetDeviceId)) throw new ArgumentException(FileTransferErrors.TargetDeviceIdCannotBeNullOrWhitespace, nameof(targetDeviceId));

        Id = id;
        FileName = fileName;
        ContentType = contentType;
        SizeBytes = sizeBytes;
        SenderUserId = senderUserId;
        TargetUserId = targetUserId;
        SenderDeviceId = senderDeviceId;
        TargetDeviceId = targetDeviceId;
        Status = status;
        CreatedAt = createdAt;
        ExpiresAt = expiresAt;
    }
}
