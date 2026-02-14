using BinaryStars.Application.Databases.DatabaseModels.Devices;
using BinaryStars.Application.Databases.Repositories.Devices;
using BinaryStars.Application.Databases.Repositories.Notes;
using BinaryStars.Application.Databases.Repositories.Transfers;
using BinaryStars.Domain;
using BinaryStars.Domain.Devices;
using BinaryStars.Domain.Errors.Devices;

namespace BinaryStars.Application.Services.Devices;

/// <summary>
/// Request payload for registering or updating a device.
/// </summary>
/// <param name="Id">The unique device identifier.</param>
/// <param name="Name">The display name for the device.</param>
/// <param name="IpAddress">The IPv4 address reported by the device.</param>
/// <param name="Ipv6Address">The optional IPv6 address.</param>
/// <param name="PublicKey">The optional public key for encryption.</param>
/// <param name="PublicKeyAlgorithm">The optional public key algorithm.</param>
public record RegisterDeviceRequest(
    string Id,
    string Name,
    string IpAddress,
    string? Ipv6Address,
    string? PublicKey,
    string? PublicKeyAlgorithm);

/// <summary>
/// Request payload for updating device telemetry.
/// </summary>
/// <param name="BatteryLevel">Battery level (0-100).</param>
/// <param name="CpuLoadPercent">Optional CPU load percentage.</param>
/// <param name="IsOnline">Whether the device is online.</param>
/// <param name="IsAvailable">Whether telemetry/data sharing is enabled.</param>
/// <param name="IsSynced">Whether the device is synced.</param>
/// <param name="WifiUploadSpeed">Current upload speed text.</param>
/// <param name="WifiDownloadSpeed">Current download speed text.</param>
public record UpdateDeviceTelemetryRequest(
    int BatteryLevel,
    int? CpuLoadPercent,
    bool IsOnline,
    bool IsAvailable,
    bool IsSynced,
    string WifiUploadSpeed,
    string WifiDownloadSpeed);

/// <summary>
/// Write-only device operations exposed by the application layer.
/// </summary>
public interface IDevicesWriteService
{
    /// <summary>
    /// Registers or updates a device linked to the specified user.
    /// </summary>
    /// <param name="userId">The user identifier.</param>
    /// <param name="request">The device registration request.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The created or updated device or a failure result.</returns>
    Task<Result<Device>> RegisterDeviceAsync(Guid userId, RegisterDeviceRequest request, CancellationToken cancellationToken);

    /// <summary>
    /// Unlinks a device and removes dependent data when allowed.
    /// </summary>
    /// <param name="userId">The user identifier.</param>
    /// <param name="deviceId">The device identifier to unlink.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>A success or failure result.</returns>
    Task<Result> UnlinkDeviceAsync(Guid userId, string deviceId, CancellationToken cancellationToken);

    /// <summary>
    /// Updates telemetry for a linked device.
    /// </summary>
    /// <param name="userId">The user identifier.</param>
    /// <param name="deviceId">The device identifier.</param>
    /// <param name="request">The telemetry payload.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The updated device or a failure result.</returns>
    Task<Result<Device>> UpdateTelemetryAsync(Guid userId, string deviceId, UpdateDeviceTelemetryRequest request, CancellationToken cancellationToken);
}

/// <summary>
/// Application service for device registration and unlinking.
/// </summary>
public class DevicesWriteService : IDevicesWriteService
{
    private readonly IDeviceRepository _deviceRepository;
    private readonly INotesRepository _notesRepository;
    private readonly IFileTransferRepository _fileTransferRepository;
    private const int MaxDevices = 5;

    public DevicesWriteService(
        IDeviceRepository deviceRepository,
        INotesRepository notesRepository,
        IFileTransferRepository fileTransferRepository)
    {
        _deviceRepository = deviceRepository;
        _notesRepository = notesRepository;
        _fileTransferRepository = fileTransferRepository;
    }

    /// <inheritdoc />
    public async Task<Result<Device>> RegisterDeviceAsync(Guid userId, RegisterDeviceRequest request, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.Id))
            return Result<Device>.Failure(DeviceErrors.IdCannotBeNullOrWhitespace);

        var existingDevice = await _deviceRepository.GetByIdAsync(request.Id, cancellationToken);
        if (existingDevice != null)
        {
            if (existingDevice.UserId == userId)
            {
                // Already registered to this user, update IP/Name potentially
                existingDevice.Name = request.Name;
                existingDevice.IpAddress = request.IpAddress;
                existingDevice.Ipv6Address = request.Ipv6Address;
                existingDevice.PublicKey = request.PublicKey ?? existingDevice.PublicKey;
                existingDevice.PublicKeyAlgorithm = request.PublicKeyAlgorithm ?? existingDevice.PublicKeyAlgorithm;
                if (!string.IsNullOrWhiteSpace(request.PublicKey))
                {
                    existingDevice.PublicKeyCreatedAt = DateTimeOffset.UtcNow;
                }
                existingDevice.LastSeen = DateTimeOffset.UtcNow;

                await _deviceRepository.SaveChangesAsync(cancellationToken);

                return Result<Device>.Success(MapToDomain(existingDevice));
            }
            else
            {
                // Registered to another user? Policy: Fail
                return Result<Device>.Failure("Device is already registered to another user.");
            }
        }

        var deviceCount = await _deviceRepository.GetCountByUserIdAsync(userId, cancellationToken);
        if (deviceCount >= MaxDevices)
        {
            return Result<Device>.Failure("Max device limit reached. This device will have view-only access.");
        }

        var newDevice = new DeviceDbModel
        {
            Id = request.Id,
            Name = request.Name,
            IpAddress = request.IpAddress,
            Ipv6Address = request.Ipv6Address,
            PublicKey = request.PublicKey,
            PublicKeyAlgorithm = request.PublicKeyAlgorithm,
            PublicKeyCreatedAt = string.IsNullOrWhiteSpace(request.PublicKey) ? null : DateTimeOffset.UtcNow,
            UserId = userId,
            BatteryLevel = 0,
            IsOnline = true,
            IsAvailable = true,
            IsSynced = false,
            CpuLoadPercent = null,
            WifiDownloadSpeed = "0 Mbps",
            WifiUploadSpeed = "0 Mbps",
            Type = DeviceType.Android,
            LastSeen = DateTimeOffset.UtcNow
        };

        await _deviceRepository.AddAsync(newDevice, cancellationToken);
        await _deviceRepository.SaveChangesAsync(cancellationToken);

        return Result<Device>.Success(MapToDomain(newDevice));
    }

    /// <inheritdoc />
    public async Task<Result<Device>> UpdateTelemetryAsync(Guid userId, string deviceId, UpdateDeviceTelemetryRequest request, CancellationToken cancellationToken)
    {
        var device = await _deviceRepository.GetByIdAsync(deviceId, cancellationToken);
        if (device == null)
            return Result<Device>.Failure("Device not found.");

        if (device.UserId != userId)
            return Result<Device>.Failure("Unauthorized.");

        device.BatteryLevel = Math.Clamp(request.BatteryLevel, 0, 100);
        device.CpuLoadPercent = request.CpuLoadPercent.HasValue
            ? Math.Clamp(request.CpuLoadPercent.Value, 0, 100)
            : null;
        device.IsOnline = request.IsOnline;
        device.IsAvailable = request.IsAvailable;
        device.IsSynced = request.IsSynced;
        device.WifiUploadSpeed = string.IsNullOrWhiteSpace(request.WifiUploadSpeed) ? "Not available" : request.WifiUploadSpeed;
        device.WifiDownloadSpeed = string.IsNullOrWhiteSpace(request.WifiDownloadSpeed) ? "Not available" : request.WifiDownloadSpeed;
        device.LastSeen = DateTimeOffset.UtcNow;

        await _deviceRepository.SaveChangesAsync(cancellationToken);
        return Result<Device>.Success(MapToDomain(device));
    }

    /// <inheritdoc />
    public async Task<Result> UnlinkDeviceAsync(Guid userId, string deviceId, CancellationToken cancellationToken)
    {
        var device = await _deviceRepository.GetByIdAsync(deviceId, cancellationToken);
        if (device == null)
            return Result.Failure("Device not found.");

        if (device.UserId != userId)
            return Result.Failure("Unauthorized.");

        var hasPendingTransfers = await _fileTransferRepository.HasPendingForDeviceAsync(deviceId, cancellationToken);
        if (hasPendingTransfers)
            return Result.Failure("Device has pending transfers and cannot be unlinked.");

        // Delete all notes associated with this device
        await _notesRepository.DeleteByDeviceIdAsync(deviceId, cancellationToken);

        await _deviceRepository.DeleteAsync(device, cancellationToken);
        await _deviceRepository.SaveChangesAsync(cancellationToken);

        return Result.Success();
    }

    private static Device MapToDomain(DeviceDbModel dbModel)
    {
        return new Device(
            dbModel.Id,
            dbModel.Name,
            dbModel.Type,
            dbModel.IpAddress,
            dbModel.Ipv6Address,
            dbModel.BatteryLevel,
            dbModel.IsOnline,
            dbModel.IsSynced,
            dbModel.WifiUploadSpeed,
            dbModel.WifiDownloadSpeed,
            dbModel.CpuLoadPercent,
            dbModel.IsAvailable,
            dbModel.LastSeen,
            dbModel.PublicKey,
            dbModel.PublicKeyAlgorithm,
            dbModel.PublicKeyCreatedAt
        );
    }
}
