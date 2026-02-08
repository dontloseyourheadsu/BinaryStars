namespace BinaryStars.Domain.Errors.Devices;

/// <summary>
/// Centralized error messages for device domain validation.
/// </summary>
public static class DeviceErrors
{
    /// <summary>
    /// Indicates the device identifier is required but missing.
    /// </summary>
    public const string IdCannotBeNullOrWhitespace = "Id cannot be null or whitespace";

    /// <summary>
    /// Indicates the device display name is required but missing.
    /// </summary>
    public const string NameCannotBeNullOrWhitespace = "Name cannot be null or whitespace";

    /// <summary>
    /// Indicates the device IP address is required but missing.
    /// </summary>
    public const string IpAddressCannotBeNullOrWhitespace = "IpAddress cannot be null or whitespace";
}
