using BinaryStars.Application.Databases.DatabaseModels.Devices;
using BinaryStars.Domain.Devices;

namespace BinaryStars.Application.Mappers.Devices;

public static class DeviceMapper
{
    public static Device ToDomain(this DeviceDbModel model)
    {
        return new Device(
            model.Id,
            model.Name,
            model.Type,
            model.IpAddress,
            model.BatteryLevel,
            model.IsOnline,
            model.IsSynced,
            model.WifiUploadSpeed,
            model.WifiDownloadSpeed,
            model.LastSeen
        );
    }

    public static DeviceDbModel ToDb(this Device domain, Guid userId)
    {
        return new DeviceDbModel
        {
            Id = domain.Id,
            Name = domain.Name,
            Type = domain.Type,
            IpAddress = domain.IpAddress,
            BatteryLevel = domain.BatteryLevel,
            IsOnline = domain.IsOnline,
            IsSynced = domain.IsSynced,
            WifiUploadSpeed = domain.WifiUploadSpeed,
            WifiDownloadSpeed = domain.WifiDownloadSpeed,
            LastSeen = domain.LastSeen,
            UserId = userId
        };
    }
}
