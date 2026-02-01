using BinaryStars.Application.Databases.DatabaseModels.Devices;

namespace BinaryStars.Application.Databases.Repositories.Devices;

public interface IDeviceRepository
{
    Task<List<DeviceDbModel>> GetDevicesByUserIdAsync(Guid userId, CancellationToken cancellationToken);
    Task<DeviceDbModel?> GetByIdAsync(string id, CancellationToken cancellationToken);
    Task AddAsync(DeviceDbModel device, CancellationToken cancellationToken);
    Task DeleteAsync(DeviceDbModel device, CancellationToken cancellationToken);
    Task<int> GetCountByUserIdAsync(Guid userId, CancellationToken cancellationToken);
    Task SaveChangesAsync(CancellationToken cancellationToken);
}
