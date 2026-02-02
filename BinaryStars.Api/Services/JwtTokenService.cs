using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using BinaryStars.Api.Models;
using BinaryStars.Application.Databases.DatabaseModels.Accounts;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;

namespace BinaryStars.Api.Services;

public class JwtTokenService
{
    private readonly JwtSettings _settings;

    public JwtTokenService(IOptions<JwtSettings> options)
    {
        _settings = options.Value;
    }

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
}
