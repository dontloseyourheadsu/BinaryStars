using BinaryStars.Domain;

namespace BinaryStars.Application.Validators.Accounts;

/// <summary>
/// Payload for registering a new account.
/// </summary>
/// <param name="Username">The requested username.</param>
/// <param name="Email">The email address.</param>
/// <param name="Password">The password for local login.</param>
public record RegisterRequest(string Username, string Email, string Password);

/// <summary>
/// Payload for logging in with a local account.
/// </summary>
/// <param name="Email">The email address or username.</param>
/// <param name="Password">The password for local login.</param>
public record LoginRequest(string Email, string Password);

/// <summary>
/// Payload for logging in with an external identity provider.
/// </summary>
/// <param name="Provider">The provider name (e.g., Google or Microsoft).</param>
/// <param name="Token">The provider-issued identity token.</param>
/// <param name="Username">Optional username for first-time registration.</param>
public record ExternalLoginRequest(string Provider, string Token, string Username);

/// <summary>
/// Validates authentication requests before they hit persistence.
/// </summary>
public class AuthValidator
{
    /// <summary>
    /// Validates a registration request.
    /// </summary>
    /// <param name="request">The registration payload.</param>
    /// <returns>A result indicating validation success or failure.</returns>
    public Result ValidateRegister(RegisterRequest request)
    {
        var errors = new List<string>();

        if (string.IsNullOrWhiteSpace(request.Username))
            errors.Add("Username is required.");

        if (string.IsNullOrWhiteSpace(request.Email))
            errors.Add("Email is required.");

        if (string.IsNullOrWhiteSpace(request.Password))
            errors.Add("Password is required.");

        return errors.Count > 0 ? Result.Failure(errors) : Result.Success();
    }

    /// <summary>
    /// Validates a login request.
    /// </summary>
    /// <param name="request">The login payload.</param>
    /// <returns>A result indicating validation success or failure.</returns>
    public Result ValidateLogin(LoginRequest request)
    {
        var errors = new List<string>();

        if (string.IsNullOrWhiteSpace(request.Email))
            errors.Add("Email is required.");

        if (string.IsNullOrWhiteSpace(request.Password))
            errors.Add("Password is required.");

        return errors.Count > 0 ? Result.Failure(errors) : Result.Success();
    }

    /// <summary>
    /// Validates an external login request.
    /// </summary>
    /// <param name="request">The external login payload.</param>
    /// <returns>A result indicating validation success or failure.</returns>
    public Result ValidateExternal(ExternalLoginRequest request)
    {
        var errors = new List<string>();

        if (string.IsNullOrWhiteSpace(request.Provider))
            errors.Add("Provider is required.");

        if (string.IsNullOrWhiteSpace(request.Token))
            errors.Add("Token is required.");

        // Username is optional for initial login check, but required for registration.
        // We will handle the requirement logic in the service.

        return errors.Count > 0 ? Result.Failure(errors) : Result.Success();
    }
}
