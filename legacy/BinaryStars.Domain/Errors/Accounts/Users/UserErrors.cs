namespace BinaryStars.Domain.Errors.Accounts.Users;

/// <summary>
/// Centralized error messages for user domain validation.
/// </summary>
public static class UserErrors
{
    /// <summary>
    /// Indicates the user identifier is required but missing.
    /// </summary>
    public const string IdCannotBeEmpty = "Id cannot be empty";

    /// <summary>
    /// Indicates the username is required but is blank or whitespace.
    /// </summary>
    public const string UsernameCannotBeNullOrWhitespace = "Username cannot be null or whitespace";

    /// <summary>
    /// Indicates the email address is required but is blank or whitespace.
    /// </summary>
    public const string EmailCannotBeNullOrWhitespace = "Email cannot be null or whitespace";
}