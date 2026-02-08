using BinaryStars.Application.Databases.Repositories.Devices;
using BinaryStars.Application.Mappers.Devices;
using BinaryStars.Domain;
using BinaryStars.Domain.Devices;

namespace BinaryStars.Application.Services.Devices;

/// <summary>
/// Read-only device operations exposed by the application layer.
/// </summary>
public interface IDevicesReadService
{
    /// <summary>
    /// Gets the devices linked to the specified user.
    /// </summary>
    /// <param name="userId">The user identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The list of devices or a failure result.</returns>
    Task<Result<List<Device>>> GetDevicesAsync(Guid userId, CancellationToken cancellationToken);
}

/// <summary>
/// Application service for reading device data.
/// </summary>
public class DevicesReadService : IDevicesReadService
{
    private readonly IDeviceRepository _deviceRepository;

    /// <summary>
    /// Initializes a new instance of the <see cref="DevicesReadService"/> class.
    /// </summary>
    /// <param name="deviceRepository">Repository for device data.</param>
    public DevicesReadService(IDeviceRepository deviceRepository)
    {
        _deviceRepository = deviceRepository;
    }

    /// <inheritdoc />
    public async Task<Result<List<Device>>> GetDevicesAsync(Guid userId, CancellationToken cancellationToken)
    {
        var deviceModels = await _deviceRepository.GetDevicesByUserIdAsync(userId, cancellationToken);
        var devices = deviceModels.Select(d => d.ToDomain()).ToList();

        return Result<List<Device>>.Success(devices);
    }
}
