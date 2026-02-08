using BinaryStars.Application.Databases.DatabaseModels.Locations;

namespace BinaryStars.Application.Databases.Repositories.Locations;

/// <summary>
/// Repository abstraction for device location history persistence.
/// </summary>
public interface ILocationHistoryRepository
{
    /// <summary>
    /// Adds a new location history record.
    /// </summary>
    /// <param name="entry">The location entry.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    Task AddAsync(LocationHistoryDbModel entry, CancellationToken cancellationToken);

    /// <summary>
    /// Gets location history for a user and device.
    /// </summary>
    /// <param name="userId">The user identifier.</param>
    /// <param name="deviceId">The device identifier.</param>
    /// <param name="limit">The maximum number of entries to return.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The list of history entries.</returns>
    Task<List<LocationHistoryDbModel>> GetByUserAndDeviceAsync(Guid userId, string deviceId, int limit, CancellationToken cancellationToken);

    /// <summary>
    /// Persists pending changes.
    /// </summary>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    Task SaveChangesAsync(CancellationToken cancellationToken);
}
