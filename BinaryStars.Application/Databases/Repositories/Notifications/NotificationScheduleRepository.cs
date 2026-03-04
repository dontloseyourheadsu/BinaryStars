using BinaryStars.Application.Databases.DatabaseContexts;
using BinaryStars.Application.Databases.DatabaseModels.Notifications;
using Microsoft.EntityFrameworkCore;

namespace BinaryStars.Application.Databases.Repositories.Notifications;

/// <summary>
/// Entity Framework repository for notification schedules.
/// </summary>
public class NotificationScheduleRepository : INotificationScheduleRepository
{
    private readonly ApplicationDbContext _context;

    /// <summary>
    /// Initializes a new instance of the <see cref="NotificationScheduleRepository"/> class.
    /// </summary>
    public NotificationScheduleRepository(ApplicationDbContext context)
    {
        _context = context;
    }

    /// <inheritdoc />
    public Task<List<NotificationScheduleDbModel>> GetByUserAndTargetDeviceAsync(Guid userId, string targetDeviceId, CancellationToken cancellationToken)
    {
        return _context.NotificationSchedules
            .Where(x => x.UserId == userId && x.TargetDeviceId == targetDeviceId)
            .OrderByDescending(x => x.UpdatedAt)
            .ToListAsync(cancellationToken);
    }

    /// <inheritdoc />
    public Task<NotificationScheduleDbModel?> GetByIdAsync(Guid id, CancellationToken cancellationToken)
    {
        return _context.NotificationSchedules
            .FirstOrDefaultAsync(x => x.Id == id, cancellationToken);
    }

    /// <inheritdoc />
    public async Task AddAsync(NotificationScheduleDbModel model, CancellationToken cancellationToken)
    {
        await _context.NotificationSchedules.AddAsync(model, cancellationToken);
    }

    /// <inheritdoc />
    public Task DeleteAsync(NotificationScheduleDbModel model, CancellationToken cancellationToken)
    {
        _context.NotificationSchedules.Remove(model);
        return Task.CompletedTask;
    }

    /// <inheritdoc />
    public Task SaveChangesAsync(CancellationToken cancellationToken)
    {
        return _context.SaveChangesAsync(cancellationToken);
    }
}
