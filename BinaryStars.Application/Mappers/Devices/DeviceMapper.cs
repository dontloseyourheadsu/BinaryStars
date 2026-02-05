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
            model.Ipv6Address,
            model.BatteryLevel,
            model.IsOnline,
            model.IsSynced,
            model.WifiUploadSpeed,
            model.WifiDownloadSpeed,
            model.LastSeen,
            model.PublicKey,
            model.PublicKeyAlgorithm,
            model.PublicKeyCreatedAt
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
            Ipv6Address = domain.Ipv6Address,
            PublicKey = domain.PublicKey,
            PublicKeyAlgorithm = domain.PublicKeyAlgorithm,
            PublicKeyCreatedAt = domain.PublicKeyCreatedAt,
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
