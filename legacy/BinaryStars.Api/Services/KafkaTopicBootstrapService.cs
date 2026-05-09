using BinaryStars.Api.Models;
using Confluent.Kafka;
using Confluent.Kafka.Admin;
using Microsoft.Extensions.Options;

namespace BinaryStars.Api.Services;

/// <summary>
/// Ensures required Kafka topics exist at API startup.
/// </summary>
public sealed class KafkaTopicBootstrapService : IHostedService
{
    private readonly ILogger<KafkaTopicBootstrapService> _logger;
    private readonly KafkaSettings _settings;

    public KafkaTopicBootstrapService(IOptions<KafkaSettings> settings, ILogger<KafkaTopicBootstrapService> logger)
    {
        _settings = settings.Value;
        _logger = logger;
    }

    public async Task StartAsync(CancellationToken cancellationToken)
    {
        try
        {
            using var admin = new AdminClientBuilder(BuildAdminConfig()).Build();

            var topicSpecs = GetRequiredTopics()
                .Select(name => new TopicSpecification
                {
                    Name = name,
                    NumPartitions = 1,
                    ReplicationFactor = 1
                })
                .ToList();

            if (topicSpecs.Count == 0)
            {
                return;
            }

            await admin.CreateTopicsAsync(topicSpecs, new CreateTopicsOptions
            {
                RequestTimeout = TimeSpan.FromSeconds(10)
            });

            _logger.LogInformation("Ensured Kafka topics exist: {Topics}", string.Join(", ", topicSpecs.Select(t => t.Name)));
        }
        catch (CreateTopicsException ex)
        {
            // Topic creation is idempotent; TopicAlreadyExists is expected after first run.
            var actionableErrors = ex.Results
                .Where(r => r.Error.Code != ErrorCode.TopicAlreadyExists && r.Error.Code != ErrorCode.NoError)
                .Select(r => $"{r.Topic}: {r.Error.Reason}")
                .ToList();

            if (actionableErrors.Count == 0)
            {
                _logger.LogDebug("Kafka topics already exist.");
                return;
            }

            _logger.LogWarning("Kafka topic bootstrap completed with errors: {Errors}", string.Join(" | ", actionableErrors));
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Kafka topic bootstrap failed; API will continue and rely on runtime retries.");
        }
    }

    public Task StopAsync(CancellationToken cancellationToken) => Task.CompletedTask;

    private AdminClientConfig BuildAdminConfig()
    {
        var config = new AdminClientConfig
        {
            BootstrapServers = !string.IsNullOrWhiteSpace(_settings.ScramBootstrapServers)
                ? _settings.ScramBootstrapServers
                : _settings.BootstrapServers
        };

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

        return config;
    }

    private IEnumerable<string> GetRequiredTopics()
    {
        var topics = new[]
        {
            _settings.Topic,
            _settings.MessagingTopic,
            _settings.DeviceRemovedTopic,
            _settings.NotificationsTopic
        };

        return topics
            .Where(t => !string.IsNullOrWhiteSpace(t))
            .Select(t => t.Trim())
            .Distinct(StringComparer.Ordinal);
    }
}
