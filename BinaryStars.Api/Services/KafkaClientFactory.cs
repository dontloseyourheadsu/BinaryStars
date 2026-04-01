using Microsoft.Extensions.Logging;
using System.IdentityModel.Tokens.Jwt;
using BinaryStars.Api.Models;
using Confluent.Kafka;
using Microsoft.Extensions.Options;

namespace BinaryStars.Api.Services;

/// <summary>
/// Builds Kafka producers and consumers with the configured security settings.
/// </summary>
public class KafkaClientFactory
{
    private readonly ILogger<KafkaClientFactory> _logger;

    private readonly KafkaSettings _settings;

    /// <summary>
    /// Initializes a new instance of the <see cref="KafkaClientFactory"/> class.
    /// </summary>
    /// <param name="settings">Kafka settings.</param>
    public KafkaClientFactory(IOptions<KafkaSettings> settings, ILogger<KafkaClientFactory> logger)
    {
        _logger = logger;

        _settings = settings.Value;
    }

    /// <summary>
    /// Creates a Kafka producer for transfer or messaging operations.
    /// </summary>
    /// <param name="authMode">The Kafka authentication mode.</param>
    /// <param name="oauthBearerToken">Optional OAuth bearer token.</param>
    /// <returns>The Kafka producer.</returns>
    public IProducer<string, byte[]> CreateProducer(KafkaAuthMode authMode, string? oauthBearerToken)
    {
        var effectiveAuthMode = ResolveEffectiveAuthMode(authMode);
        var config = new ProducerConfig
        {
            BootstrapServers = ResolveBootstrapServers(effectiveAuthMode)
        };

        ApplySecurityConfig(config, effectiveAuthMode);

        var builder = new ProducerBuilder<string, byte[]>(config);
        if (effectiveAuthMode == KafkaAuthMode.OauthBearer)
        {
            ConfigureOAuth(builder, oauthBearerToken);
        }

        return builder.Build();
    }

    /// <summary>
    /// Creates a Kafka consumer for transfer or messaging operations.
    /// </summary>
    /// <param name="groupId">The consumer group identifier.</param>
    /// <param name="authMode">The Kafka authentication mode.</param>
    /// <param name="oauthBearerToken">Optional OAuth bearer token.</param>
    /// <returns>The Kafka consumer.</returns>
    public IConsumer<string, byte[]> CreateConsumer(string groupId, KafkaAuthMode authMode, string? oauthBearerToken)
    {
        var effectiveAuthMode = ResolveEffectiveAuthMode(authMode);
        var config = new ConsumerConfig
        {
            BootstrapServers = ResolveBootstrapServers(effectiveAuthMode),
            GroupId = groupId,
            EnableAutoCommit = false,
            AutoOffsetReset = AutoOffsetReset.Earliest
        };

        ApplySecurityConfig(config, effectiveAuthMode);

        var builder = new ConsumerBuilder<string, byte[]>(config);
        if (effectiveAuthMode == KafkaAuthMode.OauthBearer)
        {
            ConfigureOAuth(builder, oauthBearerToken);
        }

        return builder.Build();
    }

    private void ApplySecurityConfig(ClientConfig config, KafkaAuthMode authMode)
    {
        if (_settings.Security.UseTls)
        {
            config.SecurityProtocol = _settings.Security.UseSasl ? SecurityProtocol.SaslSsl : SecurityProtocol.Ssl;
            config.SslCaLocation = _settings.Security.CaPath;
            config.SslCertificateLocation = _settings.Security.ClientCertPath;
            config.SslKeyLocation = _settings.Security.ClientKeyPath;
        }
        else
        {
            config.SecurityProtocol = _settings.Security.UseSasl ? SecurityProtocol.SaslPlaintext : SecurityProtocol.Plaintext;
        }

        if (_settings.Security.UseSasl)
        {
            if (authMode == KafkaAuthMode.Scram)
            {
                config.SaslMechanism = SaslMechanism.ScramSha512;
                config.SaslUsername = _settings.Scram.Username;
                config.SaslPassword = _settings.Scram.Password;
            }
            else
            {
                config.SaslMechanism = SaslMechanism.OAuthBearer;
            }
        }
    }

    private string ResolveBootstrapServers(KafkaAuthMode authMode)
    {
        var selected = authMode == KafkaAuthMode.OauthBearer
            ? _settings.OauthBootstrapServers
            : _settings.ScramBootstrapServers;

        if (!string.IsNullOrWhiteSpace(selected))
        {
            return selected;
        }

        return _settings.BootstrapServers;
    }

    private KafkaAuthMode ResolveEffectiveAuthMode(KafkaAuthMode requestedMode)
    {
        if (requestedMode == KafkaAuthMode.OauthBearer && !_settings.Oauth.UseKafkaOauth)
        {
            _logger.LogDebug("Kafka OAuth requested but UseKafkaOauth=false; falling back to SCRAM.");
            return KafkaAuthMode.Scram;
        }

        return requestedMode;
    }

    private static void ConfigureOAuth(ProducerBuilder<string, byte[]> builder, string? token)
    {
        builder.SetOAuthBearerTokenRefreshHandler((client, _) =>
        {
            if (string.IsNullOrWhiteSpace(token))
            {
                client.OAuthBearerSetTokenFailure("Missing OAuth bearer token");
                return;
            }

            var (expiresAt, principal) = ParseJwtToken(token);
            var expiresAtMs = Math.Max(DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() + 30_000, expiresAt.ToUnixTimeMilliseconds());
            client.OAuthBearerSetToken(token, expiresAtMs, principal, new Dictionary<string, string>());
        });
    }

    private static void ConfigureOAuth(ConsumerBuilder<string, byte[]> builder, string? token)
    {
        builder.SetOAuthBearerTokenRefreshHandler((client, _) =>
        {
            if (string.IsNullOrWhiteSpace(token))
            {
                client.OAuthBearerSetTokenFailure("Missing OAuth bearer token");
                return;
            }

            var (expiresAt, principal) = ParseJwtToken(token);
            var expiresAtMs = Math.Max(DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() + 30_000, expiresAt.ToUnixTimeMilliseconds());
            client.OAuthBearerSetToken(token, expiresAtMs, principal, new Dictionary<string, string>());
        });
    }

    private static (DateTimeOffset ExpiresAt, string Principal) ParseJwtToken(string token)
    {
        try
        {
            var handler = new JwtSecurityTokenHandler();
            var jwt = handler.ReadJwtToken(token);
            var exp = jwt.Payload.Expiration;
            var principal = jwt.Subject ?? jwt.Claims.FirstOrDefault(c => c.Type == "sub")?.Value ?? "binarystars";
            if (exp.HasValue)
            {
                return (DateTimeOffset.FromUnixTimeSeconds(exp.Value), principal);
            }

            return (DateTimeOffset.UtcNow.AddMinutes(30), principal);
        }
        catch (Exception)
        {
            // Can't use instance logger here
            return (DateTimeOffset.UtcNow.AddMinutes(30), "binarystars");
        }
    }
}
