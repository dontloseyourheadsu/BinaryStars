namespace BinaryStars.Api.Models;

/// <summary>
/// Response payload returned after successful authentication.
/// </summary>
/// <param name="TokenType">The authorization scheme, typically Bearer.</param>
/// <param name="AccessToken">The JWT access token.</param>
/// <param name="ExpiresIn">The access token lifetime in seconds.</param>
public record AuthResponse(string TokenType, string AccessToken, int ExpiresIn);
