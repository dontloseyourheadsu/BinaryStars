using BinaryStars.Application.Databases.DatabaseModels.Devices;
using BinaryStars.Application.Databases.Repositories.Devices;
using BinaryStars.Application.Databases.Repositories.Notes;
using BinaryStars.Application.Databases.Repositories.Transfers;
using BinaryStars.Domain;
using BinaryStars.Domain.Devices;
using BinaryStars.Domain.Errors.Devices;

namespace BinaryStars.Application.Services.Devices;

public record RegisterDeviceRequest(
    string Id,
    string Name,
    string IpAddress,
    string? Ipv6Address,
    string? PublicKey,
    string? PublicKeyAlgorithm);

public interface IDevicesWriteService
{
    Task<Result<Device>> RegisterDeviceAsync(Guid userId, RegisterDeviceRequest request, CancellationToken cancellationToken);
    Task<Result> UnlinkDeviceAsync(Guid userId, string deviceId, CancellationToken cancellationToken);
}

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
            IsSynced = false,
            WifiDownloadSpeed = "0 Mbps",
            WifiUploadSpeed = "0 Mbps",
            Type = DeviceType.Android,
            LastSeen = DateTimeOffset.UtcNow
        };

        await _deviceRepository.AddAsync(newDevice, cancellationToken);
        await _deviceRepository.SaveChangesAsync(cancellationToken);

        return Result<Device>.Success(MapToDomain(newDevice));
    }

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
            dbModel.LastSeen,
            dbModel.PublicKey,
            dbModel.PublicKeyAlgorithm,
            dbModel.PublicKeyCreatedAt
        );
    }
}
