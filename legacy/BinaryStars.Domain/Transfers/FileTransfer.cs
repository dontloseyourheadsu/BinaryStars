using BinaryStars.Domain.Errors.Transfers;

namespace BinaryStars.Domain.Transfers;

/// <summary>
/// Represents a file transfer between two devices within the same account context.
/// </summary>
public readonly record struct FileTransfer
{
    /// <summary>
    /// Gets the transfer identifier.
    /// </summary>
    public Guid Id { get; init; }

    /// <summary>
    /// Gets the original filename supplied by the sender.
    /// </summary>
    public string FileName { get; init; }

    /// <summary>
    /// Gets the MIME content type for the transfer payload.
    /// </summary>
    public string ContentType { get; init; }

    /// <summary>
    /// Gets the size of the file in bytes.
    /// </summary>
    public long SizeBytes { get; init; }

    /// <summary>
    /// Gets the sender user identifier.
    /// </summary>
    public Guid SenderUserId { get; init; }

    /// <summary>
    /// Gets the target user identifier.
    /// </summary>
    public Guid TargetUserId { get; init; }

    /// <summary>
    /// Gets the sender device identifier.
    /// </summary>
    public string SenderDeviceId { get; init; }

    /// <summary>
    /// Gets the target device identifier.
    /// </summary>
    public string TargetDeviceId { get; init; }

    /// <summary>
    /// Gets the current transfer lifecycle status.
    /// </summary>
    public FileTransferStatus Status { get; init; }

    /// <summary>
    /// Gets the timestamp when the transfer was created.
    /// </summary>
    public DateTimeOffset CreatedAt { get; init; }

    /// <summary>
    /// Gets the timestamp when the transfer expires.
    /// </summary>
    public DateTimeOffset ExpiresAt { get; init; }

    /// <summary>
    /// Initializes a new <see cref="FileTransfer"/> with validated identifiers and metadata.
    /// </summary>
    /// <param name="id">The transfer identifier.</param>
    /// <param name="fileName">The original file name.</param>
    /// <param name="contentType">The MIME content type.</param>
    /// <param name="sizeBytes">The file size in bytes.</param>
    /// <param name="senderUserId">The sender user identifier.</param>
    /// <param name="targetUserId">The target user identifier.</param>
    /// <param name="senderDeviceId">The sender device identifier.</param>
    /// <param name="targetDeviceId">The target device identifier.</param>
    /// <param name="status">The current transfer status.</param>
    /// <param name="createdAt">The transfer creation timestamp.</param>
    /// <param name="expiresAt">The transfer expiration timestamp.</param>
    /// <exception cref="ArgumentException">
    /// Thrown when required identifiers or metadata are missing or invalid.
    /// </exception>
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
