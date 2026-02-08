using System.Text.Json;
using BinaryStars.Api.Models;
using Confluent.Kafka;
using Microsoft.Extensions.Options;

namespace BinaryStars.Api.Services;

/// <summary>
/// Kafka-backed messaging service for offline delivery and device events.
/// </summary>
public class MessagingKafkaService
{
    private readonly KafkaSettings _settings;
    private readonly KafkaClientFactory _clientFactory;

    /// <summary>
    /// Initializes a new instance of the <see cref="MessagingKafkaService"/> class.
    /// </summary>
    /// <param name="settings">Kafka settings.</param>
    /// <param name="clientFactory">Kafka client factory.</param>
    public MessagingKafkaService(IOptions<KafkaSettings> settings, KafkaClientFactory clientFactory)
    {
        _settings = settings.Value;
        _clientFactory = clientFactory;
    }

    /// <summary>
    /// Publishes a device-to-device message.
    /// </summary>
    /// <param name="message">The message payload.</param>
    /// <param name="authMode">The Kafka authentication mode.</param>
    /// <param name="oauthToken">Optional OAuth bearer token.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    public async Task PublishMessageAsync(MessagingMessage message, KafkaAuthMode authMode, string? oauthToken, CancellationToken cancellationToken)
    {
        using var producer = _clientFactory.CreateProducer(authMode, oauthToken);
        var payload = JsonSerializer.SerializeToUtf8Bytes(message, MessagingJson.SerializerOptions);

        await producer.ProduceAsync(
            _settings.MessagingTopic,
            new Message<string, byte[]>
            {
                Key = message.Id,
                Value = payload
            },
            cancellationToken);

        producer.Flush(TimeSpan.FromSeconds(10));
    }

    /// <summary>
    /// Publishes a device removal event.
    /// </summary>
    /// <param name="deviceRemovedEvent">The removal event payload.</param>
    /// <param name="authMode">The Kafka authentication mode.</param>
    /// <param name="oauthToken">Optional OAuth bearer token.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    public async Task PublishDeviceRemovedAsync(DeviceRemovedEvent deviceRemovedEvent, KafkaAuthMode authMode, string? oauthToken, CancellationToken cancellationToken)
    {
        using var producer = _clientFactory.CreateProducer(authMode, oauthToken);
        var payload = JsonSerializer.SerializeToUtf8Bytes(deviceRemovedEvent, MessagingJson.SerializerOptions);

        await producer.ProduceAsync(
            _settings.DeviceRemovedTopic,
            new Message<string, byte[]>
            {
                Key = deviceRemovedEvent.Id,
                Value = payload
            },
            cancellationToken);

        producer.Flush(TimeSpan.FromSeconds(10));
    }

    /// <summary>
    /// Deletes a message from Kafka using a tombstone.
    /// </summary>
    /// <param name="messageId">The message identifier.</param>
    /// <param name="authMode">The Kafka authentication mode.</param>
    /// <param name="oauthToken">Optional OAuth bearer token.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    public async Task DeleteMessageAsync(string messageId, KafkaAuthMode authMode, string? oauthToken, CancellationToken cancellationToken)
    {
        using var producer = _clientFactory.CreateProducer(authMode, oauthToken);
        await producer.ProduceAsync(
            _settings.MessagingTopic,
            new Message<string, byte[]>
            {
                Key = messageId,
                Value = null!
            },
            cancellationToken);
        producer.Flush(TimeSpan.FromSeconds(10));
    }

    /// <summary>
    /// Deletes a device removal event from Kafka using a tombstone.
    /// </summary>
    /// <param name="eventId">The event identifier.</param>
    /// <param name="authMode">The Kafka authentication mode.</param>
    /// <param name="oauthToken">Optional OAuth bearer token.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    public async Task DeleteDeviceRemovedAsync(string eventId, KafkaAuthMode authMode, string? oauthToken, CancellationToken cancellationToken)
    {
        using var producer = _clientFactory.CreateProducer(authMode, oauthToken);
        await producer.ProduceAsync(
            _settings.DeviceRemovedTopic,
            new Message<string, byte[]>
            {
                Key = eventId,
                Value = null!
            },
            cancellationToken);
        producer.Flush(TimeSpan.FromSeconds(10));
    }

    /// <summary>
    /// Consumes pending messages for a device from Kafka.
    /// </summary>
    /// <param name="deviceId">The device identifier.</param>
    /// <param name="userId">The user identifier.</param>
    /// <param name="authMode">The Kafka authentication mode.</param>
    /// <param name="oauthToken">Optional OAuth bearer token.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The pending messages.</returns>
    public async Task<List<MessagingMessage>> ConsumePendingMessagesAsync(
        string deviceId,
        Guid userId,
        KafkaAuthMode authMode,
        string? oauthToken,
        CancellationToken cancellationToken)
    {
        using var consumer = _clientFactory.CreateConsumer($"messages-{deviceId}", authMode, oauthToken);
        consumer.Subscribe(_settings.MessagingTopic);

        var messages = new List<MessagingMessage>();
        var emptyReads = 0;

        while (!cancellationToken.IsCancellationRequested)
        {
            var result = consumer.Consume(TimeSpan.FromSeconds(1));
            if (result == null)
            {
                emptyReads++;
                if (emptyReads >= 3)
                    break;

                continue;
            }

            if (result.Message?.Value == null || result.Message.Value.Length == 0)
            {
                consumer.Commit(result);
                continue;
            }

            var message = JsonSerializer.Deserialize<MessagingMessage>(result.Message.Value, MessagingJson.SerializerOptions);
            if (message != null &&
                message.UserId == userId &&
                message.TargetDeviceId.Equals(deviceId, StringComparison.OrdinalIgnoreCase))
            {
                messages.Add(message);
            }

            consumer.Commit(result);
        }

        return messages;
    }

    /// <summary>
    /// Consumes pending device removal events for a device.
    /// </summary>
    /// <param name="deviceId">The device identifier.</param>
    /// <param name="userId">The user identifier.</param>
    /// <param name="authMode">The Kafka authentication mode.</param>
    /// <param name="oauthToken">Optional OAuth bearer token.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The pending removal events.</returns>
    public async Task<List<DeviceRemovedEvent>> ConsumePendingDeviceRemovedAsync(
        string deviceId,
        Guid userId,
        KafkaAuthMode authMode,
        string? oauthToken,
        CancellationToken cancellationToken)
    {
        using var consumer = _clientFactory.CreateConsumer($"device-removed-{deviceId}", authMode, oauthToken);
        consumer.Subscribe(_settings.DeviceRemovedTopic);

        var events = new List<DeviceRemovedEvent>();
        var emptyReads = 0;

        while (!cancellationToken.IsCancellationRequested)
        {
            var result = consumer.Consume(TimeSpan.FromSeconds(1));
            if (result == null)
            {
                emptyReads++;
                if (emptyReads >= 3)
                    break;

                continue;
            }

            if (result.Message?.Value == null || result.Message.Value.Length == 0)
            {
                consumer.Commit(result);
                continue;
            }

            var evt = JsonSerializer.Deserialize<DeviceRemovedEvent>(result.Message.Value, MessagingJson.SerializerOptions);
            if (evt != null && evt.UserId == userId)
            {
                events.Add(evt);
            }

            consumer.Commit(result);
        }

        return events;
    }
}
