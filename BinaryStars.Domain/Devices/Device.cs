using BinaryStars.Domain.Errors.Devices;

namespace BinaryStars.Domain.Devices;

/// <summary>
/// Represents a registered device in the BinaryStars ecosystem.
/// </summary>
public readonly record struct Device
{
    /// <summary>
    /// Gets the unique identifier for the device (SSAID for Android).
    /// </summary>
    public string Id { get; init; }

    /// <summary>
    /// Gets the friendly name of the device.
    /// </summary>
    public string Name { get; init; }

    /// <summary>
    /// Gets the type of the device.
    /// </summary>
    public DeviceType Type { get; init; }

    /// <summary>
    /// Gets the IPv4 address of the device.
    /// </summary>
    public string IpAddress { get; init; }

    /// <summary>
    /// Gets the IPv6 address of the device.
    /// </summary>
    public string? Ipv6Address { get; init; }

    /// <summary>
    /// Gets the current battery level of the device (0-100).
    /// </summary>
    public int BatteryLevel { get; init; }

    /// <summary>
    /// Gets a value indicating whether the device is currently online.
    /// </summary>
    public bool IsOnline { get; init; }

    /// <summary>
    /// Gets a value indicating whether the device is synced.
    /// </summary>
    public bool IsSynced { get; init; }

    /// <summary>
    /// Gets the WiFi upload speed string.
    /// </summary>
    public string WifiUploadSpeed { get; init; }

    /// <summary>
    /// Gets the WiFi download speed string.
    /// </summary>
    public string WifiDownloadSpeed { get; init; }

    /// <summary>
    /// Gets the timestamp when the device was last seen.
    /// </summary>
    public DateTimeOffset LastSeen { get; init; }

    /// <summary>
    /// Initializes a new instance of the <see cref="Device"/> struct.
    /// </summary>
    /// <param name="id">The unique identifier.</param>
    /// <param name="name">The device name.</param>
    /// <param name="type">The device type.</param>
    /// <param name="ipAddress">The IPv4 address.</param>
    /// <param name="ipv6Address">The IPv6 address.</param>
    /// <param name="batteryLevel">The battery level.</param>
    /// <param name="isOnline">Whether the device is online.</param>
    /// <param name="isSynced">Whether the device is synced.</param>
    /// <param name="wifiUploadSpeed">The upload speed.</param>
    /// <param name="wifiDownloadSpeed">The download speed.</param>
    /// <param name="lastSeen">The last seen timestamp.</param>
    public Device(
        string id,
        string name,
        DeviceType type,
        string ipAddress,
        string? ipv6Address,
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
        Ipv6Address = ipv6Address;
        BatteryLevel = batteryLevel;
        IsOnline = isOnline;
        IsSynced = isSynced;
        WifiUploadSpeed = wifiUploadSpeed;
        WifiDownloadSpeed = wifiDownloadSpeed;
        LastSeen = lastSeen;
    }
}
