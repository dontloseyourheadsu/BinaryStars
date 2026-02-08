namespace BinaryStars.Domain.Accounts.Users;

/// <summary>
/// Defines the supported authorization tiers for a user.
/// </summary>
public enum UserRole
{
    /// <summary>
    /// The account is disabled and cannot access protected features.
    /// </summary>
    Disabled,

    /// <summary>
    /// The account is on the free tier with baseline access.
    /// </summary>
    Free,

    /// <summary>
    /// The account is on a paid tier with premium access.
    /// </summary>
    Premium,
}