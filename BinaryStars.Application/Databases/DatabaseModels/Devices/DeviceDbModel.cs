using BinaryStars.Domain.Devices;

namespace BinaryStars.Application.Databases.DatabaseModels.Devices;

/// <summary>
/// Database entity representing a registered device.
/// </summary>
public class DeviceDbModel
{
    /// <summary>
    /// Gets or sets the unique device identifier.
    /// </summary>
    public required string Id { get; set; }

    /// <summary>
    /// Gets or sets the device display name.
    /// </summary>
    public required string Name { get; set; }

    /// <summary>
    /// Gets or sets the device platform type.
    /// </summary>
    public DeviceType Type { get; set; }

    /// <summary>
    /// Gets or sets the IPv4 address reported by the device.
    /// </summary>
    public required string IpAddress { get; set; }

    /// <summary>
    /// Gets or sets the optional IPv6 address reported by the device.
    /// </summary>
    public string? Ipv6Address { get; set; }

    /// <summary>
    /// Gets or sets the optional public key used for encryption.
    /// </summary>
    public string? PublicKey { get; set; }

    /// <summary>
    /// Gets or sets the optional public key algorithm.
    /// </summary>
    public string? PublicKeyAlgorithm { get; set; }

    /// <summary>
    /// Gets or sets the timestamp the public key was created.
    /// </summary>
    public DateTimeOffset? PublicKeyCreatedAt { get; set; }

    /// <summary>
    /// Gets or sets the device battery level (0-100).
    /// </summary>
    public int BatteryLevel { get; set; }

    /// <summary>
    /// Gets or sets a value indicating whether the device is online.
    /// </summary>
    public bool IsOnline { get; set; }

    /// <summary>
    /// Gets or sets a value indicating whether the device is synced.
    /// </summary>
    public bool IsSynced { get; set; }

    /// <summary>
    /// Gets or sets the WiFi upload speed string.
    /// </summary>
    public required string WifiUploadSpeed { get; set; }

    /// <summary>
    /// Gets or sets the WiFi download speed string.
    /// </summary>
    public required string WifiDownloadSpeed { get; set; }

    /// <summary>
    /// Gets or sets the timestamp when the device was last seen.
    /// </summary>
    public DateTimeOffset LastSeen { get; set; }

    /// <summary>
    /// Gets or sets the owning user identifier.
    /// </summary>
    public Guid UserId { get; set; }
}
