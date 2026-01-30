using System.IdentityModel.Tokens.Jwt;
using System.Net.Http.Headers;
using System.Text.Json.Nodes;
using Google.Apis.Auth;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using BinaryStars.Application.Validators.Accounts;

namespace BinaryStars.Application.Services.Accounts;

public class ExternalIdentityValidator
{
    private readonly IConfiguration _configuration;
    private readonly ILogger<ExternalIdentityValidator> _logger;
    private readonly IHttpClientFactory _httpClientFactory;

    public ExternalIdentityValidator(IConfiguration configuration, ILogger<ExternalIdentityValidator> logger, IHttpClientFactory httpClientFactory)
    {
        _configuration = configuration;
        _logger = logger;
        _httpClientFactory = httpClientFactory;
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
            _logger.LogError(ex, "Google token validation failed");
            return ExternalIdentityValidationResult.Failure($"Google token validation failed: {ex.Message}");
        }
    }

    private async Task<ExternalIdentityValidationResult> ValidateMicrosoftAsync(string token, CancellationToken cancellationToken)
    {
        // For Microsoft, the Android client is likely sending an Access Token for Graph API (User.Read scope).
        // Standard JWT validation fails because it's either an encrypted JWT or has a Graph Audience.
        // We validate it by calling the Microsoft Graph API /me endpoint.

        try
        {
            var client = _httpClientFactory.CreateClient();
            var request = new HttpRequestMessage(HttpMethod.Get, "https://graph.microsoft.com/v1.0/me");
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);

            var response = await client.SendAsync(request, cancellationToken);

            if (!response.IsSuccessStatusCode)
            {
                var errorContent = await response.Content.ReadAsStringAsync(cancellationToken);
                _logger.LogWarning("Microsoft Graph API call failed with status {StatusCode}: {Content}", response.StatusCode, errorContent);
                return ExternalIdentityValidationResult.Failure("Microsoft token validation failed (Graph API).");
            }

            var content = await response.Content.ReadAsStringAsync(cancellationToken);
            var json = JsonNode.Parse(content);

            var email = json?["mail"]?.ToString() ?? json?["userPrincipalName"]?.ToString();
            var id = json?["id"]?.ToString();

            if (string.IsNullOrWhiteSpace(email))
                return ExternalIdentityValidationResult.Failure("Microsoft token valid but missing email");

            return ExternalIdentityValidationResult.Success(email, id);
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
