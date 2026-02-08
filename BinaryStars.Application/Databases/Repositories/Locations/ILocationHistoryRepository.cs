using BinaryStars.Application.Databases.DatabaseModels.Locations;

namespace BinaryStars.Application.Databases.Repositories.Locations;

public interface ILocationHistoryRepository
{
    Task AddAsync(LocationHistoryDbModel entry, CancellationToken cancellationToken);
    Task<List<LocationHistoryDbModel>> GetByUserAndDeviceAsync(Guid userId, string deviceId, int limit, CancellationToken cancellationToken);
    Task SaveChangesAsync(CancellationToken cancellationToken);
}
