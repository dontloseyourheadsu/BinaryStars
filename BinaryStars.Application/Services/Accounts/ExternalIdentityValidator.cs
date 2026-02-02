using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using Google.Apis.Auth;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using Microsoft.IdentityModel.Protocols;
using Microsoft.IdentityModel.Protocols.OpenIdConnect;
using Microsoft.IdentityModel.Tokens;
using BinaryStars.Application.Validators.Accounts;

namespace BinaryStars.Application.Services.Accounts;

public class ExternalIdentityValidator
{
    private readonly IConfiguration _configuration;
    private readonly ILogger<ExternalIdentityValidator> _logger;
    private readonly IConfigurationManager<OpenIdConnectConfiguration>? _microsoftConfigManager;

    public ExternalIdentityValidator(IConfiguration configuration, ILogger<ExternalIdentityValidator> logger)
    {
        _configuration = configuration;
        _logger = logger;

        var tenantId = _configuration["Authentication:Microsoft:TenantId"];
        if (!string.IsNullOrWhiteSpace(tenantId))
        {
            var metadataAddress = $"https://login.microsoftonline.com/{tenantId}/v2.0/.well-known/openid-configuration";
            _microsoftConfigManager = new ConfigurationManager<OpenIdConnectConfiguration>(
                metadataAddress,
                new OpenIdConnectConfigurationRetriever());
        }
    }

    public async Task<ExternalIdentityValidationResult> ValidateAsync(ExternalLoginRequest request, CancellationToken cancellationToken)
    {
        return request.Provider.ToLowerInvariant() switch
        {
            "google" => await ValidateGoogleAsync(request.Token, cancellationToken),
            "microsoft" => await ValidateMicrosoftAsync(request.Token, cancellationToken),
            _ => ExternalIdentityValidationResult.Failure("Unsupported provider")
        };
    }

    private async Task<ExternalIdentityValidationResult> ValidateGoogleAsync(string token, CancellationToken cancellationToken)
    {
        var clientId = _configuration["Authentication:Google:ClientId"];
        if (string.IsNullOrWhiteSpace(clientId))
            return ExternalIdentityValidationResult.Failure("Google client id missing");

        try
        {
            var payload = await GoogleJsonWebSignature.ValidateAsync(token, new GoogleJsonWebSignature.ValidationSettings
            {
                Audience = new List<string> { clientId }
            });

            if (payload?.Email is null)
                return ExternalIdentityValidationResult.Failure("Google token missing email");

            return ExternalIdentityValidationResult.Success(payload.Email, payload.Subject);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Google token validation failed");
            return ExternalIdentityValidationResult.Failure($"Google token validation failed: {ex.Message}");
        }
    }

    private async Task<ExternalIdentityValidationResult> ValidateMicrosoftAsync(string token, CancellationToken cancellationToken)
    {
        try
        {
            if (_microsoftConfigManager == null)
                return ExternalIdentityValidationResult.Failure("Microsoft tenant id missing");

            var tenantId = _configuration["Authentication:Microsoft:TenantId"];
            var clientId = _configuration["Authentication:Microsoft:ClientId"];
            var apiAudience = _configuration["Authentication:Microsoft:ApiAudience"];

            var audiences = new List<string>();
            if (!string.IsNullOrWhiteSpace(apiAudience))
                audiences.Add(apiAudience);
            if (!string.IsNullOrWhiteSpace(clientId))
            {
                audiences.Add(clientId);
                audiences.Add($"api://{clientId}");
            }

            if (audiences.Count == 0)
                return ExternalIdentityValidationResult.Failure("Microsoft API audience not configured");

            var config = await _microsoftConfigManager.GetConfigurationAsync(cancellationToken);
            var validationParameters = new TokenValidationParameters
            {
                ValidateIssuer = true,
                ValidIssuers = new[]
                {
                    $"https://login.microsoftonline.com/{tenantId}/v2.0",
                    $"https://sts.windows.net/{tenantId}/"
                },
                ValidateAudience = true,
                ValidAudiences = audiences,
                ValidateIssuerSigningKey = true,
                IssuerSigningKeys = config.SigningKeys,
                ValidateLifetime = true,
                ClockSkew = TimeSpan.FromMinutes(2)
            };

            var handler = new JwtSecurityTokenHandler();
            var principal = handler.ValidateToken(token, validationParameters, out _);

            var email = principal.FindFirstValue(ClaimTypes.Email)
                        ?? principal.FindFirstValue("preferred_username")
                        ?? principal.FindFirstValue("upn");
            var subject = principal.FindFirstValue("oid")
                          ?? principal.FindFirstValue(ClaimTypes.NameIdentifier)
                          ?? principal.FindFirstValue("sub");

            if (string.IsNullOrWhiteSpace(email))
                return ExternalIdentityValidationResult.Failure("Microsoft token valid but missing email/username claim");

            return ExternalIdentityValidationResult.Success(email, subject);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Microsoft token validation failed");
            return ExternalIdentityValidationResult.Failure($"Microsoft token validation failed: {ex.Message}");
        }
    }
}

public record ExternalIdentityValidationResult(bool IsSuccess, string? Email, string? ProviderSubject, string? Error)
{
    public static ExternalIdentityValidationResult Success(string email, string? subject) => new(true, email, subject, null);
    public static ExternalIdentityValidationResult Failure(string error) => new(false, null, null, error);
}
