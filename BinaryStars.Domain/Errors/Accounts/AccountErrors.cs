namespace BinaryStars.Domain.Errors.Accounts;

/// <summary>
/// Centralized error messages for account domain invariants.
/// </summary>
public static class AccountErrors
{
    /// <summary>
    /// Indicates the account attempted to use a default or uninitialized user value.
    /// </summary>
    public const string UserCannotBeDefault = "User cannot be default";

    /// <summary>
    /// Indicates the account device collection was not provided.
    /// </summary>
    public const string DevicesCannotBeNull = "Devices list cannot be null";
}
