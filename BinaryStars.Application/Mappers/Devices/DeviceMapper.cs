using BinaryStars.Application.Databases.DatabaseModels.Devices;
using BinaryStars.Domain.Devices;

namespace BinaryStars.Application.Mappers.Devices;

/// <summary>
/// Maps device database models to domain models and back.
/// </summary>
public static class DeviceMapper
{
    /// <summary>
    /// Converts a database device model into the domain device model.
    /// </summary>
    /// <param name="model">The database model.</param>
    /// <returns>The domain model.</returns>
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
            model.CpuLoadPercent,
            model.IsAvailable,
            model.LastSeen,
            model.PublicKey,
            model.PublicKeyAlgorithm,
            model.PublicKeyCreatedAt
        );
    }

    /// <summary>
    /// Converts a domain device model into the database device model.
    /// </summary>
    /// <param name="domain">The domain model.</param>
    /// <param name="userId">The owning user identifier.</param>
    /// <returns>The database model.</returns>
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
            CpuLoadPercent = domain.CpuLoadPercent,
            IsAvailable = domain.IsAvailable,
            LastSeen = domain.LastSeen,
            UserId = userId
        };
    }
}
