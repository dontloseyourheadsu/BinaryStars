using BinaryStars.Application.Databases.DatabaseContexts;
using BinaryStars.Application.Databases.DatabaseModels.Locations;
using Microsoft.EntityFrameworkCore;

namespace BinaryStars.Application.Databases.Repositories.Locations;

public class LocationHistoryRepository : ILocationHistoryRepository
{
    private readonly ApplicationDbContext _context;

    public LocationHistoryRepository(ApplicationDbContext context)
    {
        _context = context;
    }

    public async Task AddAsync(LocationHistoryDbModel entry, CancellationToken cancellationToken)
    {
        await _context.LocationHistory.AddAsync(entry, cancellationToken);
    }

    public Task<List<LocationHistoryDbModel>> GetByUserAndDeviceAsync(Guid userId, string deviceId, int limit, CancellationToken cancellationToken)
    {
        return _context.LocationHistory
            .Where(l => l.UserId == userId && l.DeviceId == deviceId)
            .OrderByDescending(l => l.RecordedAt)
            .Take(limit)
            .ToListAsync(cancellationToken);
    }

    public Task SaveChangesAsync(CancellationToken cancellationToken)
    {
        return _context.SaveChangesAsync(cancellationToken);
    }
}
