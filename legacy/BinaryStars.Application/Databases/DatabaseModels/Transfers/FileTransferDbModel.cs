using BinaryStars.Domain.Transfers;

namespace BinaryStars.Application.Databases.DatabaseModels.Transfers;

/// <summary>
/// Database entity representing a file transfer record.
/// </summary>
public class FileTransferDbModel
{
    /// <summary>
    /// Gets or sets the transfer identifier.
    /// </summary>
    public required Guid Id { get; set; }

    /// <summary>
    /// Gets or sets the original file name.
    /// </summary>
    public required string FileName { get; set; }

    /// <summary>
    /// Gets or sets the MIME content type.
    /// </summary>
    public required string ContentType { get; set; }

    /// <summary>
    /// Gets or sets the file size in bytes.
    /// </summary>
    public long SizeBytes { get; set; }

    /// <summary>
    /// Gets or sets the sender user identifier.
    /// </summary>
    public required Guid SenderUserId { get; set; }

    /// <summary>
    /// Gets or sets the target user identifier.
    /// </summary>
    public required Guid TargetUserId { get; set; }

    /// <summary>
    /// Gets or sets the sender device identifier.
    /// </summary>
    public required string SenderDeviceId { get; set; }

    /// <summary>
    /// Gets or sets the target device identifier.
    /// </summary>
    public required string TargetDeviceId { get; set; }

    /// <summary>
    /// Gets or sets the current transfer status.
    /// </summary>
    public FileTransferStatus Status { get; set; }

    /// <summary>
    /// Gets or sets the failure reason, if any.
    /// </summary>
    public string? FailureReason { get; set; }

    /// <summary>
    /// Gets or sets the encryption envelope metadata.
    /// </summary>
    public string? EncryptionEnvelope { get; set; }

    /// <summary>
    /// Gets or sets the chunk size in bytes.
    /// </summary>
    public int ChunkSizeBytes { get; set; }

    /// <summary>
    /// Gets or sets the number of packets in this transfer.
    /// </summary>
    public int PacketCount { get; set; }

    /// <summary>
    /// Gets or sets the Kafka topic used for packet streaming.
    /// </summary>
    public required string KafkaTopic { get; set; }

    /// <summary>
    /// Gets or sets the Kafka partition for this transfer.
    /// </summary>
    public int? KafkaPartition { get; set; }

    /// <summary>
    /// Gets or sets the Kafka start offset.
    /// </summary>
    public long? KafkaStartOffset { get; set; }

    /// <summary>
    /// Gets or sets the Kafka end offset.
    /// </summary>
    public long? KafkaEndOffset { get; set; }

    /// <summary>
    /// Gets or sets the Kafka authentication mode.
    /// </summary>
    public required string KafkaAuthMode { get; set; }

    /// <summary>
    /// Gets or sets the creation timestamp.
    /// </summary>
    public DateTimeOffset CreatedAt { get; set; }

    /// <summary>
    /// Gets or sets the expiration timestamp.
    /// </summary>
    public DateTimeOffset ExpiresAt { get; set; }

    /// <summary>
    /// Gets or sets the completion timestamp, if any.
    /// </summary>
    public DateTimeOffset? CompletedAt { get; set; }
}
