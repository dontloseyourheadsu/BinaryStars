namespace BinaryStars.Api.Models;

public record AuthResponse(string TokenType, string AccessToken, int ExpiresIn);
