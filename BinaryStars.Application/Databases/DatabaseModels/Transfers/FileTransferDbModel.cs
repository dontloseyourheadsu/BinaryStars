using BinaryStars.Domain.Transfers;

namespace BinaryStars.Application.Databases.DatabaseModels.Transfers;

public class FileTransferDbModel
{
    public required Guid Id { get; set; }
    public required string FileName { get; set; }
    public required string ContentType { get; set; }
    public long SizeBytes { get; set; }
    public required Guid SenderUserId { get; set; }
    public required Guid TargetUserId { get; set; }
    public required string SenderDeviceId { get; set; }
    public required string TargetDeviceId { get; set; }
    public FileTransferStatus Status { get; set; }
    public string? FailureReason { get; set; }
    public string? EncryptionEnvelope { get; set; }
    public int ChunkSizeBytes { get; set; }
    public int PacketCount { get; set; }
    public required string KafkaTopic { get; set; }
    public int? KafkaPartition { get; set; }
    public long? KafkaStartOffset { get; set; }
    public long? KafkaEndOffset { get; set; }
    public required string KafkaAuthMode { get; set; }
    public DateTimeOffset CreatedAt { get; set; }
    public DateTimeOffset ExpiresAt { get; set; }
    public DateTimeOffset? CompletedAt { get; set; }
}
