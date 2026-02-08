namespace BinaryStars.Domain.Errors.Locations;

/// <summary>
/// Centralized error messages for location history validation and access rules.
/// </summary>
public static class LocationErrors
{
    /// <summary>
    /// Indicates that a request did not include a required user identifier.
    /// </summary>
    public const string UserIdCannotBeEmpty = "User ID cannot be empty";

    /// <summary>
    /// Indicates that a request did not include a required device identifier.
    /// </summary>
    public const string DeviceIdCannotBeEmpty = "Device ID cannot be empty";

    /// <summary>
    /// Indicates the device does not belong to the requesting user.
    /// </summary>
    public const string DeviceNotLinkedToUser = "Device is not linked to the user";
}
