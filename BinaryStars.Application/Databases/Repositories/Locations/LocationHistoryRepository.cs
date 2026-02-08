using BinaryStars.Application.Databases.DatabaseContexts;
using BinaryStars.Application.Databases.DatabaseModels.Locations;
using Microsoft.EntityFrameworkCore;

namespace BinaryStars.Application.Databases.Repositories.Locations;

/// <summary>
/// Entity Framework repository for location history persistence.
/// </summary>
public class LocationHistoryRepository : ILocationHistoryRepository
{
    private readonly ApplicationDbContext _context;

    /// <summary>
    /// Initializes a new instance of the <see cref="LocationHistoryRepository"/> class.
    /// </summary>
    /// <param name="context">The application database context.</param>
    public LocationHistoryRepository(ApplicationDbContext context)
    {
        _context = context;
    }

    /// <inheritdoc />
    public async Task AddAsync(LocationHistoryDbModel entry, CancellationToken cancellationToken)
    {
        await _context.LocationHistory.AddAsync(entry, cancellationToken);
    }

    /// <inheritdoc />
    public Task<List<LocationHistoryDbModel>> GetByUserAndDeviceAsync(Guid userId, string deviceId, int limit, CancellationToken cancellationToken)
    {
        return _context.LocationHistory
            .Where(l => l.UserId == userId && l.DeviceId == deviceId)
            .OrderByDescending(l => l.RecordedAt)
            .Take(limit)
            .ToListAsync(cancellationToken);
    }

    /// <inheritdoc />
    public Task SaveChangesAsync(CancellationToken cancellationToken)
    {
        return _context.SaveChangesAsync(cancellationToken);
    }
}
