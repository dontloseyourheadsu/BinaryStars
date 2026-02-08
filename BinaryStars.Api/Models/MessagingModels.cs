using System.Text.Json;

namespace BinaryStars.Api.Models;

/// <summary>
/// Message payload exchanged between devices.
/// </summary>
/// <param name="Id">The message identifier.</param>
/// <param name="UserId">The owning user identifier.</param>
/// <param name="SenderDeviceId">The sender device identifier.</param>
/// <param name="TargetDeviceId">The target device identifier.</param>
/// <param name="Body">The message body.</param>
/// <param name="SentAt">The timestamp the message was sent.</param>
public record MessagingMessage(
    string Id,
    Guid UserId,
    string SenderDeviceId,
    string TargetDeviceId,
    string Body,
    DateTimeOffset SentAt);

/// <summary>
/// Request payload for sending a message.
/// </summary>
/// <param name="SenderDeviceId">The sender device identifier.</param>
/// <param name="TargetDeviceId">The target device identifier.</param>
/// <param name="Body">The message body.</param>
/// <param name="SentAt">Optional client-provided timestamp.</param>
public record SendMessageRequest(
    string SenderDeviceId,
    string TargetDeviceId,
    string Body,
    DateTimeOffset? SentAt);

/// <summary>
/// Event emitted when a device is removed.
/// </summary>
/// <param name="Id">The event identifier.</param>
/// <param name="UserId">The owning user identifier.</param>
/// <param name="RemovedDeviceId">The removed device identifier.</param>
/// <param name="OccurredAt">The timestamp the event occurred.</param>
public record DeviceRemovedEvent(
    string Id,
    Guid UserId,
    string RemovedDeviceId,
    DateTimeOffset OccurredAt);

/// <summary>
/// Envelope used for websocket payloads.
/// </summary>
/// <param name="Type">The payload type string.</param>
/// <param name="Payload">The message payload.</param>
public record MessagingEnvelope(string Type, JsonElement Payload);
