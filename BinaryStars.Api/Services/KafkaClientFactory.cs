using BinaryStars.Api.Models;
using Confluent.Kafka;
using Microsoft.Extensions.Options;

namespace BinaryStars.Api.Services;

/// <summary>
/// Builds Kafka producers and consumers with the configured security settings.
/// </summary>
public class KafkaClientFactory
{
    private readonly KafkaSettings _settings;

    /// <summary>
    /// Initializes a new instance of the <see cref="KafkaClientFactory"/> class.
    /// </summary>
    /// <param name="settings">Kafka settings.</param>
    public KafkaClientFactory(IOptions<KafkaSettings> settings)
    {
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
        var config = new ProducerConfig
        {
            BootstrapServers = ResolveBootstrapServers()
        };

        ApplySecurityConfig(config);
        return new ProducerBuilder<string, byte[]>(config).Build();
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
        var config = new ConsumerConfig
        {
            BootstrapServers = ResolveBootstrapServers(),
            GroupId = groupId,
            EnableAutoCommit = false,
            AutoOffsetReset = AutoOffsetReset.Earliest
        };

        ApplySecurityConfig(config);
        return new ConsumerBuilder<string, byte[]>(config).Build();
    }

    private void ApplySecurityConfig(ClientConfig config)
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
            config.SaslMechanism = SaslMechanism.ScramSha512;
            config.SaslUsername = _settings.Scram.Username;
            config.SaslPassword = _settings.Scram.Password;
        }
    }

    private string ResolveBootstrapServers()
    {
        var selected = _settings.ScramBootstrapServers;

        if (!string.IsNullOrWhiteSpace(selected))
        {
            return selected;
        }

        return _settings.BootstrapServers;
    }
}
