using Microsoft.Extensions.Logging;
using BinaryStars.Application.Databases.DatabaseContexts;
using BinaryStars.Application.Databases.DatabaseModels.Locations;
using Microsoft.EntityFrameworkCore;
using System.Globalization;

namespace BinaryStars.Application.Databases.Repositories.Locations;

/// <summary>
/// Entity Framework repository for location history persistence.
/// </summary>
public class LocationHistoryRepository : ILocationHistoryRepository
{
    private readonly ILogger<LocationHistoryRepository> _logger;

    private readonly ApplicationDbContext _context;

    /// <summary>
    /// Initializes a new instance of the <see cref="LocationHistoryRepository"/> class.
    /// </summary>
    /// <param name="context">The application database context.</param>
    public LocationHistoryRepository(ApplicationDbContext context, ILogger<LocationHistoryRepository> logger)
    {
        _logger = logger;

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

    /// <inheritdoc />
    public async Task CompactOlderEntriesToHourlyAsync(Guid userId, string deviceId, DateTimeOffset detailedCutoffUtc, CancellationToken cancellationToken)
    {
        var olderEntries = await _context.LocationHistory
            .Where(entry => entry.UserId == userId && entry.DeviceId == deviceId && entry.RecordedAt < detailedCutoffUtc)
            .OrderByDescending(entry => entry.RecordedAt)
            .ToListAsync(cancellationToken);

        if (olderEntries.Count <= 1)
        {
            return;
        }

        var keepIds = olderEntries
            .GroupBy(entry => GetUtcHourBucket(entry.RecordedAt))
            .Select(group => group.First().Id)
            .ToHashSet();

        var toDelete = olderEntries
            .Where(entry => !keepIds.Contains(entry.Id))
            .ToList();

        if (toDelete.Count == 0)
        {
            return;
        }

        _context.LocationHistory.RemoveRange(toDelete);
        await _context.SaveChangesAsync(cancellationToken);
    }

    private static string GetUtcHourBucket(DateTimeOffset value)
    {
        var utc = value.UtcDateTime;
        return utc.ToString("yyyy-MM-dd-HH", CultureInfo.InvariantCulture);
    }
}
