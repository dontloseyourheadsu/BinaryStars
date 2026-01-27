using BinaryStars.Application.Databases.Repositories.Devices;
using BinaryStars.Application.Mappers.Devices;
using BinaryStars.Domain;
using BinaryStars.Domain.Devices;

namespace BinaryStars.Application.Services.Devices;

public interface IDevicesReadService
{
    Task<Result<List<Device>>> GetDevicesAsync(Guid userId, CancellationToken cancellationToken);
}

public class DevicesReadService : IDevicesReadService
{
    private readonly IDeviceRepository _deviceRepository;

    public DevicesReadService(IDeviceRepository deviceRepository)
    {
        _deviceRepository = deviceRepository;
    }

    public async Task<Result<List<Device>>> GetDevicesAsync(Guid userId, CancellationToken cancellationToken)
    {
        var deviceModels = await _deviceRepository.GetDevicesByUserIdAsync(userId, cancellationToken);
        var devices = deviceModels.Select(d => d.ToDomain()).ToList();

        return Result<List<Device>>.Success(devices);
    }
}
