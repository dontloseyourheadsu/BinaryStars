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

    /// <summary>
    /// Initializes a new instance of the <see cref="ActionsController"/> class.
    /// </summary>
    public ActionsController(
        IDeviceRepository deviceRepository,
        IAccountRepository accountRepository,
        MessagingKafkaService kafkaService)
    {
        _deviceRepository = deviceRepository;
        _accountRepository = accountRepository;
        _kafkaService = kafkaService;
    }

    /// <summary>
    /// Queues a remote action command for a Linux target device.
    /// </summary>
    /// <param name="request">The action request payload.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    [HttpPost("send")]
    public async Task<IActionResult> Send([FromBody] SendActionRequestDto request, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);

        if (!string.Equals(request.ActionType, "block_screen", StringComparison.OrdinalIgnoreCase))
            return BadRequest(new[] { "Unsupported action type." });

        var sender = await _deviceRepository.GetByIdAsync(request.SenderDeviceId, cancellationToken);
        var target = await _deviceRepository.GetByIdAsync(request.TargetDeviceId, cancellationToken);
        if (sender == null || target == null || sender.UserId != userId || target.UserId != userId)
            return BadRequest(new[] { "Invalid device." });

        if (target.Type != DeviceType.Linux)
            return BadRequest(new[] { "Actions are supported only for Linux target devices." });

        if (!target.IsOnline)
            return BadRequest(new[] { "Target device must be online." });

        var command = new DeviceActionCommand(
            Guid.NewGuid().ToString("D"),
            userId,
            request.SenderDeviceId,
            request.TargetDeviceId,
            "block_screen",
            DateTimeOffset.UtcNow);

        var authMode = await ResolveKafkaAuthModeAsync(userId);
        await _kafkaService.PublishActionAsync(command, authMode, null, cancellationToken);

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
        var actions = await _kafkaService.ConsumePendingActionsAsync(deviceId, userId, authMode, null, cancellationToken);
        foreach (var action in actions)
        {
            await _kafkaService.DeleteActionAsync(action.Id, authMode, null, cancellationToken);
        }

        return Ok(actions);
    }

    private async Task<KafkaAuthMode> ResolveKafkaAuthModeAsync(Guid userId)
    {
        var user = await _accountRepository.FindByIdAsync(userId);
        if (user == null)
            return KafkaAuthMode.Scram;

        var logins = await _accountRepository.GetLoginsAsync(user);
        return logins.Any() ? KafkaAuthMode.OauthBearer : KafkaAuthMode.Scram;
    }
}

/// <summary>
/// Request payload for remote action send.
/// </summary>
public record SendActionRequestDto(string SenderDeviceId, string TargetDeviceId, string ActionType);
