using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using BinaryStars.Api.Models;
using BinaryStars.Application.Databases.DatabaseModels.Accounts;
using BinaryStars.Domain.Accounts.Users;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;

namespace BinaryStars.Api.Services;

/// <summary>
/// Issues JWT access tokens for authenticated users.
/// </summary>
public class JwtTokenService
{
    private readonly JwtSettings _settings;

    /// <summary>
    /// Initializes a new instance of the <see cref="JwtTokenService"/> class.
    /// </summary>
    /// <param name="options">The JWT settings.</param>
    public JwtTokenService(IOptions<JwtSettings> options)
    {
        _settings = options.Value;
    }

    /// <summary>
    /// Creates a new access token for a user entity.
    /// </summary>
    /// <param name="user">The user model.</param>
    /// <returns>The authentication response with token data.</returns>
    public AuthResponse CreateToken(UserDbModel user)
    {
        var now = DateTimeOffset.UtcNow;
        var expires = now.AddMinutes(_settings.ExpiresInMinutes);

        var claims = new List<Claim>
        {
            new(ClaimTypes.NameIdentifier, user.Id.ToString()),
            new(ClaimTypes.Name, user.UserName ?? string.Empty),
            new(ClaimTypes.Email, user.Email ?? string.Empty),
            new(ClaimTypes.Role, user.Role.ToString())
        };

        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(_settings.SigningKey));
        var credentials = new SigningCredentials(key, SecurityAlgorithms.HmacSha256);

        var token = new JwtSecurityToken(
            issuer: _settings.Issuer,
            audience: _settings.Audience,
            claims: claims,
            notBefore: now.UtcDateTime,
            expires: expires.UtcDateTime,
            signingCredentials: credentials);

        var accessToken = new JwtSecurityTokenHandler().WriteToken(token);
        return new AuthResponse("Bearer", accessToken, (int)TimeSpan.FromMinutes(_settings.ExpiresInMinutes).TotalSeconds);
    }

    /// <summary>
    /// Creates a new access token from an existing claims principal.
    /// </summary>
    /// <param name="principal">The authenticated principal.</param>
    /// <returns>The authentication response with token data.</returns>
    public AuthResponse CreateTokenFromClaims(ClaimsPrincipal principal)
    {
        var userId = principal.FindFirstValue(ClaimTypes.NameIdentifier) ?? string.Empty;
        var username = principal.FindFirstValue(ClaimTypes.Name) ?? string.Empty;
        var email = principal.FindFirstValue(ClaimTypes.Email) ?? string.Empty;
        var roleValue = principal.FindFirstValue(ClaimTypes.Role) ?? UserRole.Free.ToString();

        if (!Guid.TryParse(userId, out var parsedUserId))
            parsedUserId = Guid.Empty;

        if (!Enum.TryParse<UserRole>(roleValue, out var role))
            role = UserRole.Free;

        var now = DateTimeOffset.UtcNow;
        var expires = now.AddMinutes(_settings.ExpiresInMinutes);

        var claims = new List<Claim>
        {
            new(ClaimTypes.NameIdentifier, parsedUserId.ToString()),
            new(ClaimTypes.Name, username),
            new(ClaimTypes.Email, email),
            new(ClaimTypes.Role, role.ToString())
        };

        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(_settings.SigningKey));
        var credentials = new SigningCredentials(key, SecurityAlgorithms.HmacSha256);

        var token = new JwtSecurityToken(
            issuer: _settings.Issuer,
            audience: _settings.Audience,
            claims: claims,
            notBefore: now.UtcDateTime,
            expires: expires.UtcDateTime,
            signingCredentials: credentials);

        var accessToken = new JwtSecurityTokenHandler().WriteToken(token);
        return new AuthResponse("Bearer", accessToken, (int)TimeSpan.FromMinutes(_settings.ExpiresInMinutes).TotalSeconds);
    }
}
