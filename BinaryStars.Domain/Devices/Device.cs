using BinaryStars.Domain.Errors.Devices;

namespace BinaryStars.Domain.Devices;

public readonly record struct Device
{
    public string Id { get; init; }
    public string Name { get; init; }
    public DeviceType Type { get; init; }
    public string IpAddress { get; init; }
    public int BatteryLevel { get; init; }
    public bool IsOnline { get; init; }
    public bool IsSynced { get; init; }
    public string WifiUploadSpeed { get; init; }
    public string WifiDownloadSpeed { get; init; }
    public DateTimeOffset LastSeen { get; init; }

    public Device(
        string id,
        string name,
        DeviceType type,
        string ipAddress,
        int batteryLevel,
        bool isOnline,
        bool isSynced,
        string wifiUploadSpeed,
        string wifiDownloadSpeed,
        DateTimeOffset lastSeen)
    {
        if (string.IsNullOrWhiteSpace(id)) throw new ArgumentException(DeviceErrors.IdCannotBeNullOrWhitespace, nameof(id));
        if (string.IsNullOrWhiteSpace(name)) throw new ArgumentException(DeviceErrors.NameCannotBeNullOrWhitespace, nameof(name));
        if (string.IsNullOrWhiteSpace(ipAddress)) throw new ArgumentException(DeviceErrors.IpAddressCannotBeNullOrWhitespace, nameof(ipAddress));

        Id = id;
        Name = name;
        Type = type;
        IpAddress = ipAddress;
        BatteryLevel = batteryLevel;
        IsOnline = isOnline;
        IsSynced = isSynced;
        WifiUploadSpeed = wifiUploadSpeed;
        WifiDownloadSpeed = wifiDownloadSpeed;
        LastSeen = lastSeen;
    }
}
