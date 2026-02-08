using System.Net;
using System.Net.WebSockets;
using System.Security.Claims;
using System.Text;
using System.Linq;
using BinaryStars.Api.Models;
using BinaryStars.Application.Databases.Repositories.Accounts;
using BinaryStars.Application.Databases.Repositories.Devices;

namespace BinaryStars.Api.Services;

/// <summary>
/// Handles websocket connections for device-to-device messaging.
/// </summary>
public class MessagingWebSocketHandler
{
    private const int MaxMessageLength = 500;
    private readonly MessagingConnectionManager _connectionManager;
    private readonly MessagingKafkaService _kafkaService;
    private readonly IDeviceRepository _deviceRepository;
    private readonly IAccountRepository _accountRepository;

    /// <summary>
    /// Initializes a new instance of the <see cref="MessagingWebSocketHandler"/> class.
    /// </summary>
    /// <param name="connectionManager">The connection manager.</param>
    /// <param name="kafkaService">The Kafka messaging service.</param>
    /// <param name="deviceRepository">The device repository.</param>
    /// <param name="accountRepository">The account repository.</param>
    public MessagingWebSocketHandler(
        MessagingConnectionManager connectionManager,
        MessagingKafkaService kafkaService,
        IDeviceRepository deviceRepository,
        IAccountRepository accountRepository)
    {
        _connectionManager = connectionManager;
        _kafkaService = kafkaService;
        _deviceRepository = deviceRepository;
        _accountRepository = accountRepository;
    }

    /// <summary>
    /// Accepts and processes a websocket request for messaging.
    /// </summary>
    /// <param name="context">The HTTP context.</param>
    public async Task HandleAsync(HttpContext context)
    {
        if (!context.WebSockets.IsWebSocketRequest)
        {
            context.Response.StatusCode = (int)HttpStatusCode.BadRequest;
            return;
        }

        var userIdClaim = context.User.FindFirstValue(ClaimTypes.NameIdentifier);
        if (string.IsNullOrWhiteSpace(userIdClaim) || !Guid.TryParse(userIdClaim, out var userId))
        {
            context.Response.StatusCode = (int)HttpStatusCode.Unauthorized;
            return;
        }

        var deviceId = context.Request.Query["deviceId"].ToString();
        if (string.IsNullOrWhiteSpace(deviceId))
        {
            context.Response.StatusCode = (int)HttpStatusCode.BadRequest;
            return;
        }

        var device = await _deviceRepository.GetByIdAsync(deviceId, context.RequestAborted);
        if (device == null || device.UserId != userId)
        {
            using var tempSocket = await context.WebSockets.AcceptWebSocketAsync();
            await SendRemovalOnlyAsync(tempSocket, deviceId, userId, context.RequestAborted);
            await tempSocket.CloseAsync(WebSocketCloseStatus.PolicyViolation, "Device removed", context.RequestAborted);
            return;
        }

        using var socket = await context.WebSockets.AcceptWebSocketAsync();
        _connectionManager.TryAdd(deviceId, userId, socket);

        await SendPendingAsync(socket, deviceId, userId, context.RequestAborted);

        await ReceiveLoopAsync(socket, deviceId, userId, context.RequestAborted);

        _connectionManager.TryRemove(deviceId);
    }

    private async Task SendPendingAsync(WebSocket socket, string deviceId, Guid userId, CancellationToken cancellationToken)
    {
        var authMode = await ResolveKafkaAuthModeAsync(userId, cancellationToken);

        var pendingMessages = await _kafkaService.ConsumePendingMessagesAsync(deviceId, userId, authMode, null, cancellationToken);
        foreach (var message in pendingMessages)
        {
            await SendEnvelopeAsync(socket, "message", message, cancellationToken);
            await _kafkaService.DeleteMessageAsync(message.Id, authMode, null, cancellationToken);
        }

        var pendingRemoved = await _kafkaService.ConsumePendingDeviceRemovedAsync(deviceId, userId, authMode, null, cancellationToken);
        foreach (var removal in pendingRemoved)
        {
            await SendEnvelopeAsync(socket, "device_removed", removal, cancellationToken);
            await _kafkaService.DeleteDeviceRemovedAsync(removal.Id, authMode, null, cancellationToken);
        }
    }

    private async Task SendRemovalOnlyAsync(WebSocket socket, string deviceId, Guid userId, CancellationToken cancellationToken)
    {
        var authMode = await ResolveKafkaAuthModeAsync(userId, cancellationToken);
        var pendingRemoved = await _kafkaService.ConsumePendingDeviceRemovedAsync(deviceId, userId, authMode, null, cancellationToken);
        foreach (var removal in pendingRemoved.Where(r => r.RemovedDeviceId.Equals(deviceId, StringComparison.OrdinalIgnoreCase)))
        {
            await SendEnvelopeAsync(socket, "device_removed", removal, cancellationToken);
            await _kafkaService.DeleteDeviceRemovedAsync(removal.Id, authMode, null, cancellationToken);
        }
    }

    private async Task ReceiveLoopAsync(WebSocket socket, string deviceId, Guid userId, CancellationToken cancellationToken)
    {
        var buffer = new byte[8192];
        while (socket.State == WebSocketState.Open && !cancellationToken.IsCancellationRequested)
        {
            var result = await socket.ReceiveAsync(new ArraySegment<byte>(buffer), cancellationToken);
            if (result.MessageType == WebSocketMessageType.Close)
            {
                await socket.CloseAsync(WebSocketCloseStatus.NormalClosure, "Closing", cancellationToken);
                break;
            }

            if (result.MessageType != WebSocketMessageType.Text)
                continue;

            var messageText = await ReadMessageAsync(socket, result, buffer, cancellationToken);
            if (string.IsNullOrWhiteSpace(messageText))
                continue;

            var envelope = MessagingJson.DeserializeEnvelope(messageText);
            if (envelope == null)
                continue;

            if (envelope.Type.Equals("message", StringComparison.OrdinalIgnoreCase))
            {
                await HandleIncomingMessageAsync(envelope, deviceId, userId, cancellationToken);
            }
        }
    }

    private async Task HandleIncomingMessageAsync(MessagingEnvelope envelope, string deviceId, Guid userId, CancellationToken cancellationToken)
    {
        if (!MessagingJson.TryReadPayload<SendMessageRequest>(envelope, out var request) || request == null)
            return;

        if (!deviceId.Equals(request.SenderDeviceId, StringComparison.OrdinalIgnoreCase))
            return;

        if (string.IsNullOrWhiteSpace(request.Body) || request.Body.Length > MaxMessageLength)
            return;

        var targetDevice = await _deviceRepository.GetByIdAsync(request.TargetDeviceId, cancellationToken);
        if (targetDevice == null || targetDevice.UserId != userId)
            return;

        var sentAt = request.SentAt ?? DateTimeOffset.UtcNow;
        var message = new MessagingMessage(
            Guid.NewGuid().ToString("D"),
            userId,
            request.SenderDeviceId,
            request.TargetDeviceId,
            request.Body,
            sentAt);

        if (_connectionManager.TryGet(request.TargetDeviceId, out var connection) && connection?.Socket.State == WebSocketState.Open)
        {
            await SendEnvelopeAsync(connection.Socket, "message", message, cancellationToken);
        }
        else
        {
            var authMode = await ResolveKafkaAuthModeAsync(userId, cancellationToken);
            await _kafkaService.PublishMessageAsync(message, authMode, null, cancellationToken);
        }
    }

    private async Task SendEnvelopeAsync<T>(WebSocket socket, string type, T payload, CancellationToken cancellationToken)
    {
        var json = MessagingJson.SerializeEnvelope(type, payload);
        var bytes = Encoding.UTF8.GetBytes(json);
        await socket.SendAsync(new ArraySegment<byte>(bytes), WebSocketMessageType.Text, true, cancellationToken);
    }

    private static async Task<string> ReadMessageAsync(WebSocket socket, WebSocketReceiveResult initial, byte[] buffer, CancellationToken cancellationToken)
    {
        var builder = new StringBuilder();
        builder.Append(Encoding.UTF8.GetString(buffer, 0, initial.Count));

        var result = initial;
        while (!result.EndOfMessage)
        {
            result = await socket.ReceiveAsync(new ArraySegment<byte>(buffer), cancellationToken);
            if (result.MessageType != WebSocketMessageType.Text)
                break;

            builder.Append(Encoding.UTF8.GetString(buffer, 0, result.Count));
        }

        return builder.ToString();
    }

    private async Task<KafkaAuthMode> ResolveKafkaAuthModeAsync(Guid userId, CancellationToken cancellationToken)
    {
        var user = await _accountRepository.FindByIdAsync(userId);
        if (user == null)
            return KafkaAuthMode.Scram;

        var logins = await _accountRepository.GetLoginsAsync(user);
        return logins.Any() ? KafkaAuthMode.OauthBearer : KafkaAuthMode.Scram;
    }
}
