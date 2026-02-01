using BinaryStars.Domain.Devices;

namespace BinaryStars.Application.Databases.DatabaseModels.Devices;

public class DeviceDbModel
{
    public required string Id { get; set; }
    public required string Name { get; set; }
    public DeviceType Type { get; set; }
    public required string IpAddress { get; set; }
    public string? Ipv6Address { get; set; }
    public int BatteryLevel { get; set; }
    public bool IsOnline { get; set; }
    public bool IsSynced { get; set; }
    public required string WifiUploadSpeed { get; set; }
    public required string WifiDownloadSpeed { get; set; }
    public DateTimeOffset LastSeen { get; set; }

    // Foreign Key
    public Guid UserId { get; set; }
}
