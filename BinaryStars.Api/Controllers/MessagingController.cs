using System.Security.Claims;
using BinaryStars.Api.Models;
using BinaryStars.Api.Services;
using BinaryStars.Application.Databases.Repositories.Accounts;
using BinaryStars.Application.Databases.Repositories.Devices;
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
    private const int MaxMessageLength = 500;
    private readonly MessagingConnectionManager _connectionManager;
    private readonly MessagingKafkaService _kafkaService;
    private readonly IDeviceRepository _deviceRepository;
    private readonly IAccountRepository _accountRepository;

    /// <summary>
    /// Initializes a new instance of the <see cref="MessagingController"/> class.
    /// </summary>
    /// <param name="connectionManager">The websocket connection manager.</param>
    /// <param name="kafkaService">The Kafka messaging service.</param>
    /// <param name="deviceRepository">The device repository.</param>
    /// <param name="accountRepository">The account repository.</param>
    public MessagingController(
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

        var sentAt = request.SentAt ?? DateTimeOffset.UtcNow;
        var message = new MessagingMessage(
            Guid.NewGuid().ToString("D"),
            userId,
            request.SenderDeviceId,
            request.TargetDeviceId,
            request.Body,
            sentAt);

        if (_connectionManager.TryGet(request.TargetDeviceId, out var connection) && connection?.Socket.State == System.Net.WebSockets.WebSocketState.Open)
        {
            var payload = MessagingJson.SerializeEnvelope("message", message);
            var bytes = System.Text.Encoding.UTF8.GetBytes(payload);
            await connection.Socket.SendAsync(new ArraySegment<byte>(bytes), System.Net.WebSockets.WebSocketMessageType.Text, true, cancellationToken);
        }
        else
        {
            var authMode = await ResolveKafkaAuthModeAsync(userId, cancellationToken);
            await _kafkaService.PublishMessageAsync(message, authMode, null, cancellationToken);
        }

        return Ok(message);
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
