using System.Security.Claims;
using System.Net.WebSockets;
using System.Text;
using BinaryStars.Api.Models;
using BinaryStars.Api.Services;
using BinaryStars.Application.Databases.Repositories.Devices;
using BinaryStars.Domain.Devices;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace BinaryStars.Api.Controllers;

/// <summary>
/// Provides remote device action endpoints.
/// </summary>
[ApiController]
[Route("api/[controller]")]
[Authorize]
public class ActionsController : ControllerBase
{
    private readonly IDeviceRepository _deviceRepository;
    private readonly MessagingConnectionManager _connectionManager;
    private readonly ILogger<ActionsController> _logger;

    /// <summary>
    /// Initializes a new instance of the <see cref="ActionsController"/> class.
    /// </summary>
    public ActionsController(
        IDeviceRepository deviceRepository,
        MessagingConnectionManager connectionManager,
        ILogger<ActionsController> logger)
    {
        _deviceRepository = deviceRepository;
        _connectionManager = connectionManager;
        _logger = logger;
    }

    /// <summary>
    /// Queues a remote action command for a Linux target device.
    /// </summary>
    /// <param name="request">The action request payload.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    [HttpPost("send")]
    public async Task<IActionResult> Send([FromBody] SendActionRequestDto request, CancellationToken cancellationToken)
    {
        _logger.LogInformation("Received action request {ActionType} from {Sender} to {Target}", request.ActionType, request.SenderDeviceId, request.TargetDeviceId);

        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);

        var normalizedActionType = NormalizeActionType(request.ActionType);
        if (normalizedActionType == null)
        {
            _logger.LogDebug("Unsupported action type requested: {ActionType}", request.ActionType);
            return BadRequest(new[] { "Unsupported action type." });
        }

        var sender = await _deviceRepository.GetByIdAsync(request.SenderDeviceId, cancellationToken);
        var target = await _deviceRepository.GetByIdAsync(request.TargetDeviceId, cancellationToken);
        if (sender == null || target == null || sender.UserId != userId || target.UserId != userId)
        {
            _logger.LogWarning("Invalid device mapping for User {UserId}. Sender={Sender}, Target={Target}", userId, sender?.Id, target?.Id);
            return BadRequest(new[] { "Invalid device." });
        }

        if (target.Type != DeviceType.Linux)
        {
            _logger.LogDebug("Target device {TargetId} is not Linux.", target.Id);
            return BadRequest(new[] { "Actions are supported only for Linux target devices." });
        }

        if (!target.IsOnline)
        {
            _logger.LogDebug("Target device {TargetId} is offline.", target.Id);
            return BadRequest(new[] { "Target device must be online." });
        }

        _logger.LogTrace("Constructing DeviceActionCommand for {ActionType}", normalizedActionType);
        var command = new DeviceActionCommand(
            Guid.NewGuid().ToString("D"),
            userId,
            request.SenderDeviceId,
            request.TargetDeviceId,
            normalizedActionType,
            request.PayloadJson,
            request.CorrelationId,
            DateTimeOffset.UtcNow);

        if (!_connectionManager.TryGet(target.Id, out var targetConnection)
            || targetConnection?.Socket.State != WebSocketState.Open)
        {
            return BadRequest(new[] { "Target device is not connected to realtime channel." });
        }

        _logger.LogInformation("Dispatching realtime action command {CommandId} to target websocket", command.Id);
        await SendEnvelopeAsync(targetConnection.Socket, "action_command", command, cancellationToken);

        return Ok(command);
    }

    /// <summary>
    /// Pulls pending remote action commands for a device and removes them from Kafka.
    /// </summary>
    /// <param name="deviceId">The target device identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    [HttpGet("pull")]
    public async Task<IActionResult> Pull([FromQuery] string deviceId, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);

        var target = await _deviceRepository.GetByIdAsync(deviceId, cancellationToken);
        if (target == null || target.UserId != userId)
            return BadRequest(new[] { "Invalid device." });

        return Ok(Array.Empty<DeviceActionCommand>());
    }

    /// <summary>
    /// Publishes an action result message from target device to requester.
    /// </summary>
    [HttpPost("results")]
    public async Task<IActionResult> PublishResult([FromBody] PublishActionResultRequestDto request, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);

        var normalizedActionType = NormalizeActionType(request.ActionType) ?? request.ActionType.Trim().ToLowerInvariant();

        var sender = await _deviceRepository.GetByIdAsync(request.SenderDeviceId, cancellationToken);
        var target = await _deviceRepository.GetByIdAsync(request.TargetDeviceId, cancellationToken);
        if (sender == null || target == null || sender.UserId != userId || target.UserId != userId)
            return BadRequest(new[] { "Invalid device." });

        var result = new DeviceActionResultMessage(
            Guid.NewGuid().ToString("D"),
            userId,
            request.SenderDeviceId,
            request.TargetDeviceId,
            normalizedActionType,
            request.Status,
            request.PayloadJson,
            request.Error,
            request.CorrelationId,
            DateTimeOffset.UtcNow);

        if (!_connectionManager.TryGet(result.TargetDeviceId, out var requesterConnection)
            || requesterConnection?.Socket.State != WebSocketState.Open)
        {
            return BadRequest(new[] { "Requester device is not connected to realtime channel." });
        }

        await SendEnvelopeAsync(requesterConnection.Socket, "action_result", result, cancellationToken);

        return Ok(result);
    }

    /// <summary>
    /// Pulls pending action result messages for a device and removes them from Kafka.
    /// </summary>
    [HttpGet("results/pull")]
    public async Task<IActionResult> PullResults([FromQuery] string deviceId, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);

        var target = await _deviceRepository.GetByIdAsync(deviceId, cancellationToken);
        if (target == null || target.UserId != userId)
            return BadRequest(new[] { "Invalid device." });

        return Ok(Array.Empty<DeviceActionResultMessage>());
    }

    private static async Task SendEnvelopeAsync<T>(WebSocket socket, string type, T payload, CancellationToken cancellationToken)
    {
        var json = MessagingJson.SerializeEnvelope(type, payload);
        var bytes = Encoding.UTF8.GetBytes(json);
        await socket.SendAsync(new ArraySegment<byte>(bytes), WebSocketMessageType.Text, true, cancellationToken);
    }

    private static string? NormalizeActionType(string actionType)
    {
        if (string.Equals(actionType, "block_screen", StringComparison.OrdinalIgnoreCase))
            return "block_screen";

        if (string.Equals(actionType, "list_installed_apps", StringComparison.OrdinalIgnoreCase))
            return "list_installed_apps";

        if (string.Equals(actionType, "list_running_apps", StringComparison.OrdinalIgnoreCase))
            return "list_running_apps";

        if (string.Equals(actionType, "launch_app", StringComparison.OrdinalIgnoreCase))
            return "launch_app";

        if (string.Equals(actionType, "close_app", StringComparison.OrdinalIgnoreCase))
            return "close_app";

        if (string.Equals(actionType, "get_clipboard_history", StringComparison.OrdinalIgnoreCase))
            return "get_clipboard_history";

        if (string.Equals(actionType, "shutdown", StringComparison.OrdinalIgnoreCase))
            return "shutdown";

        if (string.Equals(actionType, "reboot", StringComparison.OrdinalIgnoreCase) ||
            string.Equals(actionType, "reset", StringComparison.OrdinalIgnoreCase))
            return "reboot";

        return null;
    }

}

/// <summary>
/// Request payload for remote action send.
/// </summary>
public record SendActionRequestDto(
    string SenderDeviceId,
    string TargetDeviceId,
    string ActionType,
    string? PayloadJson,
    string? CorrelationId);

/// <summary>
/// Request payload for action result publish.
/// </summary>
public record PublishActionResultRequestDto(
    string SenderDeviceId,
    string TargetDeviceId,
    string ActionType,
    string Status,
    string? PayloadJson,
    string? Error,
    string? CorrelationId);
