using BinaryStars.Application.Databases.DatabaseModels.Devices;

namespace BinaryStars.Application.Databases.Repositories.Devices;

public interface IDeviceRepository
{
    Task<List<DeviceDbModel>> GetDevicesByUserIdAsync(Guid userId, CancellationToken cancellationToken);
}
