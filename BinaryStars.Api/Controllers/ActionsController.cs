using System.Security.Claims;
using BinaryStars.Api.Models;
using BinaryStars.Api.Services;
using BinaryStars.Application.Databases.Repositories.Accounts;
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
    private readonly IAccountRepository _accountRepository;
    private readonly MessagingKafkaService _kafkaService;
    private readonly ILogger<ActionsController> _logger;

    /// <summary>
    /// Initializes a new instance of the <see cref="ActionsController"/> class.
    /// </summary>
    public ActionsController(
        IDeviceRepository deviceRepository,
        IAccountRepository accountRepository,
        MessagingKafkaService kafkaService,
        ILogger<ActionsController> logger)
    {
        _deviceRepository = deviceRepository;
        _accountRepository = accountRepository;
        _kafkaService = kafkaService;
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

        var authMode = await ResolveKafkaAuthModeAsync(userId);
        var oauthToken = authMode == KafkaAuthMode.OauthBearer ? ExtractBearerToken() : null;

        _logger.LogInformation("Publishing command {CommandId} to Kafka", command.CommandId);
        await _kafkaService.PublishActionAsync(command, authMode, oauthToken, cancellationToken);

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

        var authMode = await ResolveKafkaAuthModeAsync(userId);
        var oauthToken = authMode == KafkaAuthMode.OauthBearer ? ExtractBearerToken() : null;
        var actions = await _kafkaService.ConsumePendingActionsAsync(deviceId, userId, authMode, oauthToken, cancellationToken);
        foreach (var action in actions)
        {
            await _kafkaService.DeleteActionAsync(action.Id, authMode, oauthToken, cancellationToken);
        }

        return Ok(actions);
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

        var authMode = await ResolveKafkaAuthModeAsync(userId);
        var oauthToken = authMode == KafkaAuthMode.OauthBearer ? ExtractBearerToken() : null;
        await _kafkaService.PublishActionResultAsync(result, authMode, oauthToken, cancellationToken);

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

        var authMode = await ResolveKafkaAuthModeAsync(userId);
        var oauthToken = authMode == KafkaAuthMode.OauthBearer ? ExtractBearerToken() : null;
        var results = await _kafkaService.ConsumePendingActionResultsAsync(deviceId, userId, authMode, oauthToken, cancellationToken);
        foreach (var result in results)
        {
            await _kafkaService.DeleteActionResultAsync(result.Id, authMode, oauthToken, cancellationToken);
        }

        return Ok(results);
    }

    private static string? NormalizeActionType(string actionType)
    {
        if (string.Equals(actionType, "block_screen", StringComparison.OrdinalIgnoreCase))
            return "block_screen";

        if (string.Equals(actionType, "list_launchable_apps", StringComparison.OrdinalIgnoreCase))
            return "list_launchable_apps";

        if (string.Equals(actionType, "list_running_apps", StringComparison.OrdinalIgnoreCase))
            return "list_running_apps";

        if (string.Equals(actionType, "open_app", StringComparison.OrdinalIgnoreCase))
            return "open_app";

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

    private async Task<KafkaAuthMode> ResolveKafkaAuthModeAsync(Guid userId)
    {
        var user = await _accountRepository.FindByIdAsync(userId);
        if (user == null)
            return KafkaAuthMode.Scram;

        var logins = await _accountRepository.GetLoginsAsync(user);
        return logins.Any() ? KafkaAuthMode.OauthBearer : KafkaAuthMode.Scram;
    }

    private string? ExtractBearerToken()
    {
        var header = Request.Headers.Authorization.ToString();
        if (header.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
            return header["Bearer ".Length..].Trim();

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
