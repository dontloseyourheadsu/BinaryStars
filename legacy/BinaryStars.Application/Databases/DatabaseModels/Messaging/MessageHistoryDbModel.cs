namespace BinaryStars.Application.Databases.DatabaseModels.Messaging;

/// <summary>
/// Database entity representing a persisted device chat message.
/// </summary>
public class MessageHistoryDbModel
{
    /// <summary>
    /// Gets or sets the message identifier.
    /// </summary>
    public required Guid Id { get; set; }

    /// <summary>
    /// Gets or sets the owning user identifier.
    /// </summary>
    public required Guid UserId { get; set; }

    /// <summary>
    /// Gets or sets the sender device identifier.
    /// </summary>
    public required string SenderDeviceId { get; set; }

    /// <summary>
    /// Gets or sets the target device identifier.
    /// </summary>
    public required string TargetDeviceId { get; set; }

    /// <summary>
    /// Gets or sets the message body.
    /// </summary>
    public required string Body { get; set; }

    /// <summary>
    /// Gets or sets when the message was sent.
    /// </summary>
    public DateTimeOffset SentAt { get; set; }

    /// <summary>
    /// Gets or sets when this row was created.
    /// </summary>
    public DateTimeOffset CreatedAt { get; set; }
}
