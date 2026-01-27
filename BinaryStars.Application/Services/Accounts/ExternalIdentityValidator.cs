using System.IdentityModel.Tokens.Jwt;
using Google.Apis.Auth;
using Microsoft.Extensions.Configuration;
using Microsoft.IdentityModel.Protocols;
using Microsoft.IdentityModel.Protocols.OpenIdConnect;
using Microsoft.IdentityModel.Tokens;
using BinaryStars.Application.Validators.Accounts;

namespace BinaryStars.Application.Services.Accounts;

public class ExternalIdentityValidator
{
    private readonly IConfiguration _configuration;
    private readonly IConfigurationManager<OpenIdConnectConfiguration> _microsoftConfigManager;

    public ExternalIdentityValidator(IConfiguration configuration)
    {
        _configuration = configuration;

        var tenantId = _configuration["AzureAd:TenantId"] ?? string.Empty;
        var authority = $"https://login.microsoftonline.com/{tenantId}/v2.0";
        _microsoftConfigManager = new ConfigurationManager<OpenIdConnectConfiguration>(
            $"{authority}/.well-known/openid-configuration",
            new OpenIdConnectConfigurationRetriever());
    }

    public async Task<ExternalIdentityValidationResult> ValidateAsync(ExternalLoginRequest request, CancellationToken cancellationToken)
    {
        return request.Provider.ToLowerInvariant() switch
        {
            "google" => await ValidateGoogleAsync(request.IdToken, cancellationToken),
            "microsoft" => await ValidateMicrosoftAsync(request.IdToken, cancellationToken),
            _ => ExternalIdentityValidationResult.Failure("Unsupported provider")
        };
    }

    private async Task<ExternalIdentityValidationResult> ValidateGoogleAsync(string idToken, CancellationToken cancellationToken)
    {
        var clientId = _configuration["Authentication:Google:ClientId"];
        if (string.IsNullOrWhiteSpace(clientId))
            return ExternalIdentityValidationResult.Failure("Google client id missing");

        try
        {
            var payload = await GoogleJsonWebSignature.ValidateAsync(idToken, new GoogleJsonWebSignature.ValidationSettings
            {
                Audience = new List<string> { clientId }
            });

            if (payload?.Email is null)
                return ExternalIdentityValidationResult.Failure("Google token missing email");

            return ExternalIdentityValidationResult.Success(payload.Email, payload.Subject);
        }
        catch (Exception ex)
        {
            return ExternalIdentityValidationResult.Failure($"Google token validation failed: {ex.Message}");
        }
    }

    private async Task<ExternalIdentityValidationResult> ValidateMicrosoftAsync(string idToken, CancellationToken cancellationToken)
    {
        var tenantId = _configuration["AzureAd:TenantId"];
        var clientId = _configuration["AzureAd:ClientId"];
        if (string.IsNullOrWhiteSpace(tenantId) || string.IsNullOrWhiteSpace(clientId))
            return ExternalIdentityValidationResult.Failure("Microsoft configuration missing");

        var authority = $"https://login.microsoftonline.com/{tenantId}/v2.0";

        try
        {
            var config = await _microsoftConfigManager.GetConfigurationAsync(cancellationToken);
            var validationParameters = new TokenValidationParameters
            {
                ValidIssuer = config.Issuer,
                ValidAudiences = new[] { clientId },
                IssuerSigningKeys = config.SigningKeys,
                ValidateLifetime = true,
                ValidateIssuerSigningKey = true,
                ValidateAudience = true,
                ValidateIssuer = true
            };

            var handler = new JwtSecurityTokenHandler();
            var principal = handler.ValidateToken(idToken, validationParameters, out _);

            var email = principal.FindFirst("preferred_username")?.Value
                       ?? principal.FindFirst("email")?.Value
                       ?? principal.FindFirst("upn")?.Value;

            if (string.IsNullOrWhiteSpace(email))
                return ExternalIdentityValidationResult.Failure("Microsoft token missing email");

            var subject = principal.FindFirst(JwtRegisteredClaimNames.Sub)?.Value ?? string.Empty;
            return ExternalIdentityValidationResult.Success(email, subject);
        }
        catch (Exception ex)
        {
            return ExternalIdentityValidationResult.Failure($"Microsoft token validation failed: {ex.Message}");
        }
    }
}

public record ExternalIdentityValidationResult(bool IsSuccess, string? Email, string? ProviderSubject, string? Error)
{
    public static ExternalIdentityValidationResult Success(string email, string? subject) => new(true, email, subject, null);
    public static ExternalIdentityValidationResult Failure(string error) => new(false, null, null, error);
}
