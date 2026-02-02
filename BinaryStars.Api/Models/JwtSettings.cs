namespace BinaryStars.Api.Models;

public class JwtSettings
{
    public string Issuer { get; init; } = string.Empty;
    public string Audience { get; init; } = string.Empty;
    public string SigningKey { get; init; } = string.Empty;
    public int ExpiresInMinutes { get; init; } = 120;
}
