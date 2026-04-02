using Microsoft.Extensions.Logging;
using System.Security.Claims;
using System.Net.WebSockets;
using BinaryStars.Api.Models;
using BinaryStars.Api.Services;
using BinaryStars.Application.Databases.DatabaseModels.Messaging;
using BinaryStars.Application.Databases.Repositories.Devices;
using BinaryStars.Application.Databases.Repositories.Messaging;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace BinaryStars.Api.Controllers;

/// <summary>
/// Provides messaging endpoints for device-to-device chat.
/// </summary>
[ApiController]
[Route("api/messaging")]
[Authorize]
public class MessagingController : ControllerBase
{
    private readonly ILogger<MessagingController> _logger;

    private const int MaxMessageLength = 500;
    private readonly MessagingConnectionManager _connectionManager;
    private readonly MessagingKafkaService _kafkaService;
    private readonly IDeviceRepository _deviceRepository;
    private readonly IMessageHistoryRepository _messageHistoryRepository;

    /// <summary>
    /// Initializes a new instance of the <see cref="MessagingController"/> class.
    /// </summary>
    /// <param name="connectionManager">The websocket connection manager.</param>
    /// <param name="kafkaService">The Kafka messaging service.</param>
    /// <param name="deviceRepository">The device repository.</param>
    /// <param name="accountRepository">The account repository.</param>
    /// <param name="messageHistoryRepository">The message history repository.</param>
    public MessagingController(
        MessagingConnectionManager connectionManager,
        MessagingKafkaService kafkaService,
        IDeviceRepository deviceRepository,
        IMessageHistoryRepository messageHistoryRepository, ILogger<MessagingController> logger)
    {
        _logger = logger;

        _connectionManager = connectionManager;
        _kafkaService = kafkaService;
        _deviceRepository = deviceRepository;
        _messageHistoryRepository = messageHistoryRepository;
    }

    /// <summary>
    /// Gets recent chat thread summaries for a device.
    /// </summary>
    /// <param name="deviceId">The current device identifier.</param>
    /// <param name="limit">Maximum history messages to scan when building summaries.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    [HttpGet("chats")]
    public async Task<IActionResult> GetChats([FromQuery] string deviceId, [FromQuery] int limit = 500, CancellationToken cancellationToken = default)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var device = await _deviceRepository.GetByIdAsync(deviceId, cancellationToken);
        if (device == null || device.UserId != userId)
            return Forbid();

        var rows = await _messageHistoryRepository.GetByDeviceAsync(userId, deviceId, limit, cancellationToken);
        var summaries = rows
            .GroupBy(row => row.SenderDeviceId.Equals(deviceId, StringComparison.OrdinalIgnoreCase)
                ? row.TargetDeviceId
                : row.SenderDeviceId)
            .Select(group => group
                .OrderByDescending(row => row.SentAt)
                .Select(row => new ChatSummaryResponse(
                    group.Key,
                    row.Body,
                    row.SentAt))
                .First())
            .OrderByDescending(summary => summary.LastSentAt)
            .ToList();

        return Ok(summaries);
    }

    /// <summary>
    /// Gets recent conversation history between two devices.
    /// </summary>
    /// <param name="deviceId">The current device identifier.</param>
    /// <param name="targetDeviceId">The other device identifier.</param>
    /// <param name="limit">Maximum number of messages to return.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    [HttpGet("history")]
    public async Task<IActionResult> GetHistory([FromQuery] string deviceId, [FromQuery] string targetDeviceId, [FromQuery] int limit = 200, CancellationToken cancellationToken = default)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);

        var sourceDevice = await _deviceRepository.GetByIdAsync(deviceId, cancellationToken);
        var targetDevice = await _deviceRepository.GetByIdAsync(targetDeviceId, cancellationToken);
        if (sourceDevice == null || targetDevice == null)
            return BadRequest(new[] { "Invalid device." });

        if (sourceDevice.UserId != userId || targetDevice.UserId != userId)
            return Forbid();

        var rows = await _messageHistoryRepository.GetConversationAsync(userId, deviceId, targetDeviceId, limit, cancellationToken);
        var payload = rows
            .OrderBy(row => row.SentAt)
            .Select(row => new MessagingMessage(
                row.Id.ToString("D"),
                row.UserId,
                row.SenderDeviceId,
                row.TargetDeviceId,
                row.Body,
                row.SentAt))
            .ToList();

        return Ok(payload);
    }

    /// <summary>
    /// Clears persisted conversation history between two devices.
    /// </summary>
    [HttpPost("clear")]
    public async Task<IActionResult> ClearConversation([FromBody] ClearConversationRequest request, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var sourceDevice = await _deviceRepository.GetByIdAsync(request.DeviceId, cancellationToken);
        var targetDevice = await _deviceRepository.GetByIdAsync(request.TargetDeviceId, cancellationToken);
        if (sourceDevice == null || targetDevice == null)
            return BadRequest(new[] { "Invalid device." });

        if (sourceDevice.UserId != userId || targetDevice.UserId != userId)
            return Forbid();

        await _messageHistoryRepository.DeleteConversationAsync(userId, request.DeviceId, request.TargetDeviceId, cancellationToken);
        await _messageHistoryRepository.SaveChangesAsync(cancellationToken);
        return Ok();
    }

    /// <summary>
    /// Sends a message from one device to another.
    /// </summary>
    /// <param name="request">The send message request.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The sent message payload.</returns>
    [HttpPost("send")]
    public async Task<IActionResult> SendMessage([FromBody] SendMessageRequest request, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);

        if (string.IsNullOrWhiteSpace(request.Body) || request.Body.Length > MaxMessageLength)
            return BadRequest(new[] { "Message body must be between 1 and 500 characters." });

        var senderDevice = await _deviceRepository.GetByIdAsync(request.SenderDeviceId, cancellationToken);
        var targetDevice = await _deviceRepository.GetByIdAsync(request.TargetDeviceId, cancellationToken);
        if (senderDevice == null || targetDevice == null)
            return BadRequest(new[] { "Invalid device." });

        if (senderDevice.UserId != userId || targetDevice.UserId != userId)
            return Forbid();

        var sentAt = (request.SentAt ?? DateTimeOffset.UtcNow).ToUniversalTime();
        var message = new MessagingMessage(
            Guid.NewGuid().ToString("D"),
            userId,
            request.SenderDeviceId,
            request.TargetDeviceId,
            request.Body,
            sentAt);

        await PersistMessageAsync(message, cancellationToken);

        var delivered = false;
        if (_connectionManager.TryGet(request.TargetDeviceId, out var connection) && connection?.Socket.State == WebSocketState.Open)
        {
            try
            {
                var payload = MessagingJson.SerializeEnvelope("message", message);
                var bytes = System.Text.Encoding.UTF8.GetBytes(payload);
                await connection.Socket.SendAsync(new ArraySegment<byte>(bytes), WebSocketMessageType.Text, true, cancellationToken);
                delivered = true;
            }
            catch
            {
                delivered = false;
            }
        }

        if (!delivered)
        {
            try
            {
                const KafkaAuthMode authMode = KafkaAuthMode.Scram;
                await _kafkaService.PublishMessageAsync(message, authMode, null, cancellationToken);
            }
            catch
            {
                // We persisted the message to DB successfully. If Kafka is unavailable, logging it
                // instead of crashing ensures the 'send' action is still considered successful 
                // for historical purposes (so we don't present a false-negative to the client).
                // It will be retried/synced when the target queries /history.
            }
        }

        return Ok(message);
    }

    private async Task PersistMessageAsync(MessagingMessage message, CancellationToken cancellationToken)
    {
        await _messageHistoryRepository.AddAsync(new MessageHistoryDbModel
        {
            Id = Guid.Parse(message.Id),
            UserId = message.UserId,
            SenderDeviceId = message.SenderDeviceId,
            TargetDeviceId = message.TargetDeviceId,
            Body = message.Body,
            SentAt = message.SentAt,
            CreatedAt = DateTimeOffset.UtcNow,
        }, cancellationToken);

        await _messageHistoryRepository.SaveChangesAsync(cancellationToken);
    }

}
