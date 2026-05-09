namespace BinaryStars.Api.Models;

/// <summary>
/// Configuration for issuing JWT access tokens.
/// </summary>
public class JwtSettings
{
    /// <summary>
    /// Gets the token issuer.
    /// </summary>
    public string Issuer { get; init; } = string.Empty;

    /// <summary>
    /// Gets the token audience.
    /// </summary>
    public string Audience { get; init; } = string.Empty;

    /// <summary>
    /// Gets the signing key used for token generation.
    /// </summary>
    public string SigningKey { get; init; } = string.Empty;

    /// <summary>
    /// Gets the token lifetime in minutes.
    /// </summary>
    public int ExpiresInMinutes { get; init; } = 120;
}
