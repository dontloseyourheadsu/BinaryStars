namespace BinaryStars.Application.Databases.DatabaseModels.Notifications;

/// <summary>
/// Database entity representing a notification schedule definition shared to a target device.
/// </summary>
public class NotificationScheduleDbModel
{
    /// <summary>
    /// Gets or sets the schedule identifier.
    /// </summary>
    public Guid Id { get; set; }

    /// <summary>
    /// Gets or sets the owning user identifier.
    /// </summary>
    public Guid UserId { get; set; }

    /// <summary>
    /// Gets or sets the source device that created or last updated the schedule.
    /// </summary>
    public required string SourceDeviceId { get; set; }

    /// <summary>
    /// Gets or sets the target device that should apply the schedule locally.
    /// </summary>
    public required string TargetDeviceId { get; set; }

    /// <summary>
    /// Gets or sets the notification title.
    /// </summary>
    public required string Title { get; set; }

    /// <summary>
    /// Gets or sets the notification body.
    /// </summary>
    public required string Body { get; set; }

    /// <summary>
    /// Gets or sets whether the schedule is active.
    /// </summary>
    public bool IsEnabled { get; set; }

    /// <summary>
    /// Gets or sets the one-time execution timestamp in UTC.
    /// </summary>
    public DateTimeOffset? ScheduledForUtc { get; set; }

    /// <summary>
    /// Gets or sets the repeat cadence in minutes for recurring schedules.
    /// </summary>
    public int? RepeatMinutes { get; set; }

    /// <summary>
    /// Gets or sets the creation timestamp.
    /// </summary>
    public DateTimeOffset CreatedAt { get; set; }

    /// <summary>
    /// Gets or sets the update timestamp.
    /// </summary>
    public DateTimeOffset UpdatedAt { get; set; }
}
