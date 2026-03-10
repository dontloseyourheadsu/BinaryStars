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
/// Event emitted when a device online presence changes.
/// </summary>
/// <param name="Id">The event identifier.</param>
/// <param name="UserId">The owning user identifier.</param>
/// <param name="DeviceId">The device identifier.</param>
/// <param name="IsOnline">Whether the device is online.</param>
/// <param name="LastSeen">The latest heartbeat timestamp for the device.</param>
/// <param name="OccurredAt">The timestamp the event occurred.</param>
public record DevicePresenceEvent(
    string Id,
    Guid UserId,
    string DeviceId,
    bool IsOnline,
    DateTimeOffset LastSeen,
    DateTimeOffset OccurredAt);

/// <summary>
/// Event emitted when a device reports a fresh location update.
/// </summary>
/// <param name="Id">The event identifier.</param>
/// <param name="UserId">The owning user identifier.</param>
/// <param name="DeviceId">The source device identifier.</param>
/// <param name="Latitude">The reported latitude.</param>
/// <param name="Longitude">The reported longitude.</param>
/// <param name="AccuracyMeters">Optional GPS accuracy in meters.</param>
/// <param name="RecordedAt">The timestamp attached to the location sample.</param>
/// <param name="OccurredAt">The server timestamp when this event was emitted.</param>
public record LocationUpdateEvent(
    string Id,
    Guid UserId,
    string DeviceId,
    double Latitude,
    double Longitude,
    double? AccuracyMeters,
    DateTimeOffset RecordedAt,
    DateTimeOffset OccurredAt);

/// <summary>
/// Payload for device notifications queued for pull-by-heartbeat delivery.
/// </summary>
/// <param name="Id">The notification identifier.</param>
/// <param name="UserId">The owning user identifier.</param>
/// <param name="SenderDeviceId">The source device identifier.</param>
/// <param name="TargetDeviceId">The target device identifier.</param>
/// <param name="Title">The notification title.</param>
/// <param name="Body">The notification body.</param>
/// <param name="CreatedAt">The timestamp the notification was queued.</param>
public record DeviceNotificationMessage(
    string Id,
    Guid UserId,
    string SenderDeviceId,
    string TargetDeviceId,
    string Title,
    string Body,
    DateTimeOffset CreatedAt);

/// <summary>
/// Payload for remote device actions delivered via Kafka pull.
/// </summary>
/// <param name="Id">The action identifier.</param>
/// <param name="UserId">The owning user identifier.</param>
/// <param name="SenderDeviceId">The source device identifier.</param>
/// <param name="TargetDeviceId">The target device identifier.</param>
/// <param name="ActionType">The action type string (for example: block_screen).</param>
/// <param name="PayloadJson">Optional JSON payload for action parameters.</param>
/// <param name="CreatedAt">The timestamp the action was queued.</param>
public record DeviceActionCommand(
    string Id,
    Guid UserId,
    string SenderDeviceId,
    string TargetDeviceId,
    string ActionType,
    string? PayloadJson,
    string? CorrelationId,
    DateTimeOffset CreatedAt);

/// <summary>
/// Payload for action result messages returned from target to requester.
/// </summary>
public record DeviceActionResultMessage(
    string Id,
    Guid UserId,
    string SenderDeviceId,
    string TargetDeviceId,
    string ActionType,
    string Status,
    string? PayloadJson,
    string? Error,
    string? CorrelationId,
    DateTimeOffset CreatedAt);

/// <summary>
/// Envelope used for websocket payloads.
/// </summary>
/// <param name="Type">The payload type string.</param>
/// <param name="Payload">The message payload.</param>
public record MessagingEnvelope(string Type, JsonElement Payload);
