using System.IdentityModel.Tokens.Jwt;
using BinaryStars.Api.Models;
using Confluent.Kafka;
using Microsoft.Extensions.Options;

namespace BinaryStars.Api.Services;

public class KafkaClientFactory
{
    private readonly KafkaSettings _settings;

    public KafkaClientFactory(IOptions<KafkaSettings> settings)
    {
        _settings = settings.Value;
    }

    public IProducer<string, byte[]> CreateProducer(KafkaAuthMode authMode, string? oauthBearerToken)
    {
        var config = new ProducerConfig
        {
            BootstrapServers = _settings.BootstrapServers
        };

        ApplySecurityConfig(config, authMode);

        var builder = new ProducerBuilder<string, byte[]>(config);
        if (authMode == KafkaAuthMode.OauthBearer)
        {
            ConfigureOAuth(builder, oauthBearerToken);
        }

        return builder.Build();
    }

    public IConsumer<string, byte[]> CreateConsumer(string groupId, KafkaAuthMode authMode, string? oauthBearerToken)
    {
        var config = new ConsumerConfig
        {
            BootstrapServers = _settings.BootstrapServers,
            GroupId = groupId,
            EnableAutoCommit = false,
            AutoOffsetReset = AutoOffsetReset.Earliest
        };

        ApplySecurityConfig(config, authMode);

        var builder = new ConsumerBuilder<string, byte[]>(config);
        if (authMode == KafkaAuthMode.OauthBearer)
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
            var expiresInSeconds = Math.Max(30, (int)(expiresAt - DateTimeOffset.UtcNow).TotalSeconds);
            client.OAuthBearerSetToken(token, expiresInSeconds, principal, new Dictionary<string, string>());
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
            var expiresInSeconds = Math.Max(30, (int)(expiresAt - DateTimeOffset.UtcNow).TotalSeconds);
            client.OAuthBearerSetToken(token, expiresInSeconds, principal, new Dictionary<string, string>());
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
            return (DateTimeOffset.UtcNow.AddMinutes(30), "binarystars");
        }
    }
}
