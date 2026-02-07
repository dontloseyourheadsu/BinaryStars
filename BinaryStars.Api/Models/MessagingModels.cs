using System.Text.Json;

namespace BinaryStars.Api.Models;

public record MessagingMessage(
    string Id,
    Guid UserId,
    string SenderDeviceId,
    string TargetDeviceId,
    string Body,
    DateTimeOffset SentAt);

public record SendMessageRequest(
    string SenderDeviceId,
    string TargetDeviceId,
    string Body,
    DateTimeOffset? SentAt);

public record DeviceRemovedEvent(
    string Id,
    Guid UserId,
    string RemovedDeviceId,
    DateTimeOffset OccurredAt);

public record MessagingEnvelope(string Type, JsonElement Payload);
