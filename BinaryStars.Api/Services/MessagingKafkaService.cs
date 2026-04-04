using Microsoft.Extensions.Logging;
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
    private readonly ILogger<MessagingKafkaService> _logger;

    private readonly KafkaSettings _settings;
    private readonly KafkaClientFactory _clientFactory;

    /// <summary>
    /// Initializes a new instance of the <see cref="MessagingKafkaService"/> class.
    /// </summary>
    /// <param name="settings">Kafka settings.</param>
    /// <param name="clientFactory">Kafka client factory.</param>
    public MessagingKafkaService(IOptions<KafkaSettings> settings, KafkaClientFactory clientFactory, ILogger<MessagingKafkaService> logger)
    {
        _logger = logger;

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
    /// Publishes a device notification.
    /// </summary>
    /// <param name="message">The notification payload.</param>
    /// <param name="authMode">The Kafka authentication mode.</param>
    /// <param name="oauthToken">Optional OAuth bearer token.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    public async Task PublishNotificationAsync(DeviceNotificationMessage message, KafkaAuthMode authMode, string? oauthToken, CancellationToken cancellationToken)
    {
        using var producer = _clientFactory.CreateProducer(authMode, oauthToken);
        var payload = JsonSerializer.SerializeToUtf8Bytes(message, MessagingJson.SerializerOptions);

        await producer.ProduceAsync(
            _settings.NotificationsTopic,
            new Message<string, byte[]>
            {
                Key = message.Id,
                Value = payload
            },
            cancellationToken);

        producer.Flush(TimeSpan.FromSeconds(10));
    }

    /// <summary>
    /// Publishes a remote action command for a device.
    /// </summary>
    /// <param name="message">The action payload.</param>
    /// <param name="authMode">The Kafka authentication mode.</param>
    /// <param name="oauthToken">Optional OAuth bearer token.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    public async Task PublishActionAsync(DeviceActionCommand message, KafkaAuthMode authMode, string? oauthToken, CancellationToken cancellationToken)
    {
        using var producer = _clientFactory.CreateProducer(authMode, oauthToken);
        var payload = JsonSerializer.SerializeToUtf8Bytes(message, MessagingJson.SerializerOptions);

        await producer.ProduceAsync(
            _settings.ActionsTopic,
            new Message<string, byte[]>
            {
                Key = message.Id,
                Value = payload
            },
            cancellationToken);

        producer.Flush(TimeSpan.FromSeconds(10));
    }

    /// <summary>
    /// Publishes a remote action result message.
    /// </summary>
    public async Task PublishActionResultAsync(DeviceActionResultMessage message, KafkaAuthMode authMode, string? oauthToken, CancellationToken cancellationToken)
    {
        using var producer = _clientFactory.CreateProducer(authMode, oauthToken);
        var payload = JsonSerializer.SerializeToUtf8Bytes(message, MessagingJson.SerializerOptions);

        await producer.ProduceAsync(
            _settings.ActionResultsTopic,
            new Message<string, byte[]>
            {
                Key = message.Id,
                Value = payload
            },
            cancellationToken);

        producer.Flush(TimeSpan.FromSeconds(10));
    }

    /// <summary>
    /// Consumes pending notifications for a device from Kafka.
    /// </summary>
    /// <param name="deviceId">The device identifier.</param>
    /// <param name="userId">The user identifier.</param>
    /// <param name="authMode">The Kafka authentication mode.</param>
    /// <param name="oauthToken">Optional OAuth bearer token.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The pending notifications.</returns>
    public async Task<List<DeviceNotificationMessage>> ConsumePendingNotificationsAsync(
        string deviceId,
        Guid userId,
        KafkaAuthMode authMode,
        string? oauthToken,
        CancellationToken cancellationToken)
    {
        using var consumer = _clientFactory.CreateConsumer($"notifications-{deviceId}", authMode, oauthToken);
        consumer.Subscribe(_settings.NotificationsTopic);

        var notifications = new List<DeviceNotificationMessage>();
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

            var message = JsonSerializer.Deserialize<DeviceNotificationMessage>(result.Message.Value, MessagingJson.SerializerOptions);
            if (message != null &&
                message.UserId == userId &&
                message.TargetDeviceId.Equals(deviceId, StringComparison.OrdinalIgnoreCase))
            {
                notifications.Add(message);
            }

            consumer.Commit(result);
        }

        return notifications;
    }

    /// <summary>
    /// Deletes a pending notification from Kafka using a tombstone.
    /// </summary>
    /// <param name="notificationId">The notification identifier.</param>
    /// <param name="authMode">The Kafka authentication mode.</param>
    /// <param name="oauthToken">Optional OAuth bearer token.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    public async Task DeleteNotificationAsync(string notificationId, KafkaAuthMode authMode, string? oauthToken, CancellationToken cancellationToken)
    {
        using var producer = _clientFactory.CreateProducer(authMode, oauthToken);
        await producer.ProduceAsync(
            _settings.NotificationsTopic,
            new Message<string, byte[]>
            {
                Key = notificationId,
                Value = null!
            },
            cancellationToken);
        producer.Flush(TimeSpan.FromSeconds(10));
    }

    /// <summary>
    /// Consumes pending action commands for a device from Kafka.
    /// </summary>
    /// <param name="deviceId">The device identifier.</param>
    /// <param name="userId">The user identifier.</param>
    /// <param name="authMode">The Kafka authentication mode.</param>
    /// <param name="oauthToken">Optional OAuth bearer token.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The pending action commands.</returns>
    public async Task<List<DeviceActionCommand>> ConsumePendingActionsAsync(
        string deviceId,
        Guid userId,
        KafkaAuthMode authMode,
        string? oauthToken,
        CancellationToken cancellationToken)
    {
        using var consumer = _clientFactory.CreateConsumer($"actions-{deviceId}", authMode, oauthToken);
        consumer.Subscribe(_settings.ActionsTopic);

        var commands = new List<DeviceActionCommand>();
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

            var command = JsonSerializer.Deserialize<DeviceActionCommand>(result.Message.Value, MessagingJson.SerializerOptions);
            if (command != null &&
                command.UserId == userId &&
                command.TargetDeviceId.Equals(deviceId, StringComparison.OrdinalIgnoreCase))
            {
                commands.Add(command);
            }

            consumer.Commit(result);
        }

        return commands;
    }

    /// <summary>
    /// Deletes a pending action command from Kafka using a tombstone.
    /// </summary>
    /// <param name="actionId">The action command identifier.</param>
    /// <param name="authMode">The Kafka authentication mode.</param>
    /// <param name="oauthToken">Optional OAuth bearer token.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    public async Task DeleteActionAsync(string actionId, KafkaAuthMode authMode, string? oauthToken, CancellationToken cancellationToken)
    {
        using var producer = _clientFactory.CreateProducer(authMode, oauthToken);
        await producer.ProduceAsync(
            _settings.ActionsTopic,
            new Message<string, byte[]>
            {
                Key = actionId,
                Value = null!
            },
            cancellationToken);
        producer.Flush(TimeSpan.FromSeconds(10));
    }

    /// <summary>
    /// Consumes pending action result messages for a device.
    /// </summary>
    public async Task<List<DeviceActionResultMessage>> ConsumePendingActionResultsAsync(
        string deviceId,
        Guid userId,
        KafkaAuthMode authMode,
        string? oauthToken,
        CancellationToken cancellationToken)
    {
        using var consumer = _clientFactory.CreateConsumer($"action-results-{deviceId}", authMode, oauthToken);
        consumer.Subscribe(_settings.ActionResultsTopic);

        var messages = new List<DeviceActionResultMessage>();
        var emptyReads = 0;

        while (!cancellationToken.IsCancellationRequested)
        {
            ConsumeResult<string, byte[]>? result;
            try
            {
                result = consumer.Consume(TimeSpan.FromMilliseconds(250));
            }
            catch (ConsumeException ex) when (ex.Error.Code == ErrorCode.UnknownTopicOrPart)
            {
                _logger.LogWarning("Action results topic '{Topic}' is not available yet; returning empty result set.", _settings.ActionResultsTopic);
                return messages;
            }

            if (result == null)
            {
                emptyReads++;
                if (emptyReads >= 1)
                    break;

                continue;
            }

            if (result.Message?.Value == null || result.Message.Value.Length == 0)
            {
                consumer.Commit(result);
                continue;
            }

            var message = JsonSerializer.Deserialize<DeviceActionResultMessage>(result.Message.Value, MessagingJson.SerializerOptions);
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
    /// Deletes an action result message from Kafka using a tombstone.
    /// </summary>
    public async Task DeleteActionResultAsync(string resultId, KafkaAuthMode authMode, string? oauthToken, CancellationToken cancellationToken)
    {
        using var producer = _clientFactory.CreateProducer(authMode, oauthToken);
        await producer.ProduceAsync(
            _settings.ActionResultsTopic,
            new Message<string, byte[]>
            {
                Key = resultId,
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
