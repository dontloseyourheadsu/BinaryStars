using BinaryStars.Application.Databases.DatabaseModels.Devices;

namespace BinaryStars.Application.Databases.Repositories.Devices;

/// <summary>
/// Repository abstraction for device persistence.
/// </summary>
public interface IDeviceRepository
{
    /// <summary>
    /// Gets all devices linked to the specified user.
    /// </summary>
    /// <param name="userId">The user identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The list of devices.</returns>
    Task<List<DeviceDbModel>> GetDevicesByUserIdAsync(Guid userId, CancellationToken cancellationToken);

    /// <summary>
    /// Gets a device by identifier.
    /// </summary>
    /// <param name="id">The device identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The device, or null if not found.</returns>
    Task<DeviceDbModel?> GetByIdAsync(string id, CancellationToken cancellationToken);

    /// <summary>
    /// Adds a new device record.
    /// </summary>
    /// <param name="device">The device to add.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    Task AddAsync(DeviceDbModel device, CancellationToken cancellationToken);

    /// <summary>
    /// Removes a device record.
    /// </summary>
    /// <param name="device">The device to remove.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    Task DeleteAsync(DeviceDbModel device, CancellationToken cancellationToken);

    /// <summary>
    /// Counts the number of devices linked to a user.
    /// </summary>
    /// <param name="userId">The user identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The device count.</returns>
    Task<int> GetCountByUserIdAsync(Guid userId, CancellationToken cancellationToken);

    /// <summary>
    /// Persists pending changes.
    /// </summary>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    Task SaveChangesAsync(CancellationToken cancellationToken);
}
