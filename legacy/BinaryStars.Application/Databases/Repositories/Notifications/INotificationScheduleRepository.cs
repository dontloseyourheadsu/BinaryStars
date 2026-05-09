using BinaryStars.Application.Databases.DatabaseModels.Notifications;

namespace BinaryStars.Application.Databases.Repositories.Notifications;

/// <summary>
/// Repository abstraction for notification schedule persistence.
/// </summary>
public interface INotificationScheduleRepository
{
    /// <summary>
    /// Gets all schedules for a user and target device.
    /// </summary>
    Task<List<NotificationScheduleDbModel>> GetByUserAndTargetDeviceAsync(Guid userId, string targetDeviceId, CancellationToken cancellationToken);

    /// <summary>
    /// Gets a schedule by identifier.
    /// </summary>
    Task<NotificationScheduleDbModel?> GetByIdAsync(Guid id, CancellationToken cancellationToken);

    /// <summary>
    /// Adds a new schedule.
    /// </summary>
    Task AddAsync(NotificationScheduleDbModel model, CancellationToken cancellationToken);

    /// <summary>
    /// Removes a schedule.
    /// </summary>
    Task DeleteAsync(NotificationScheduleDbModel model, CancellationToken cancellationToken);

    /// <summary>
    /// Persists pending changes.
    /// </summary>
    Task SaveChangesAsync(CancellationToken cancellationToken);
}
