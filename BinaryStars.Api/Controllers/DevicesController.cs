using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using BinaryStars.Application.Services.Devices;
using System.Security.Claims;
using BinaryStars.Api.Models;
using BinaryStars.Api.Services;
using BinaryStars.Application.Databases.Repositories.Accounts;
using System.Net.WebSockets;
using System.Text;

namespace BinaryStars.Api.Controllers;

/// <summary>
/// Provides device management endpoints.
/// </summary>
[ApiController]
[Route("api/[controller]")]
[Authorize] // Ensure authentication is required
public class DevicesController : ControllerBase
{
    private readonly IDevicesReadService _devicesReadService;
    private readonly IDevicesWriteService _devicesWriteService;
    private readonly MessagingKafkaService _messagingKafkaService;
    private readonly MessagingConnectionManager _connectionManager;
    private readonly IAccountRepository _accountRepository;

    /// <summary>
    /// Initializes a new instance of the <see cref="DevicesController"/> class.
    /// </summary>
    /// <param name="devicesReadService">The device read service.</param>
    /// <param name="devicesWriteService">The device write service.</param>
    /// <param name="messagingKafkaService">The Kafka messaging service.</param>
    /// <param name="connectionManager">The websocket connection manager.</param>
    /// <param name="accountRepository">The account repository.</param>
    public DevicesController(
        IDevicesReadService devicesReadService,
        IDevicesWriteService devicesWriteService,
        MessagingKafkaService messagingKafkaService,
        MessagingConnectionManager connectionManager,
        IAccountRepository accountRepository)
    {
        _devicesReadService = devicesReadService;
        _devicesWriteService = devicesWriteService;
        _messagingKafkaService = messagingKafkaService;
        _connectionManager = connectionManager;
        _accountRepository = accountRepository;
    }

    /// <summary>
    /// Gets devices linked to the authenticated user.
    /// </summary>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The list of devices.</returns>
    [HttpGet]
    public async Task<IActionResult> GetDevices(CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);

        var result = await _devicesReadService.GetDevicesAsync(userId, cancellationToken);
        if (result.IsSuccess)
        {
            return Ok(result.Value);
        }
        return BadRequest(result.Errors);
    }

    /// <summary>
    /// Registers or updates a device for the authenticated user.
    /// </summary>
    /// <param name="request">The device registration request.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The registered device.</returns>
    [HttpPost("register")]
    public async Task<IActionResult> RegisterDevice([FromBody] RegisterDeviceRequest request, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var result = await _devicesWriteService.RegisterDeviceAsync(userId, request, cancellationToken);

        if (result.IsSuccess)
            return Ok(result.Value);

        // If failure is due to limit reached, we still return Ok but maybe with a warning?
        // User asked: "if limit is reached, then this device will only have access to view devices... still leave them a button... if they say no..."
        // The service returns Failure if limit reached with message "Max device limit reached. This device will have view-only access."
        // We can treat this specific failure as a specialized response code or just BadRequest

        return BadRequest(result.Errors);
    }

    /// <summary>
    /// Unlinks a device from the authenticated user.
    /// </summary>
    /// <param name="deviceId">The device identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>Ok on success.</returns>
    [HttpDelete("{deviceId}")]
    public async Task<IActionResult> UnlinkDevice(string deviceId, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var result = await _devicesWriteService.UnlinkDeviceAsync(userId, deviceId, cancellationToken);


        if (result.IsSuccess)
        {
            var removalEvent = new DeviceRemovedEvent(
                Guid.NewGuid().ToString("D"),
                userId,
                deviceId,
                DateTimeOffset.UtcNow);

            var authMode = await ResolveKafkaAuthModeAsync(userId);
            await _messagingKafkaService.PublishDeviceRemovedAsync(removalEvent, authMode, null, cancellationToken);

            await NotifyConnectedDevicesAsync(userId, removalEvent, cancellationToken);
            return Ok();
        }

        return BadRequest(result.Errors);
    }

    private async Task<KafkaAuthMode> ResolveKafkaAuthModeAsync(Guid userId)
    {
        var user = await _accountRepository.FindByIdAsync(userId);
        if (user == null)
            return KafkaAuthMode.Scram;

        var logins = await _accountRepository.GetLoginsAsync(user);
        return logins.Any() ? KafkaAuthMode.OauthBearer : KafkaAuthMode.Scram;
    }

    private async Task NotifyConnectedDevicesAsync(Guid userId, DeviceRemovedEvent removalEvent, CancellationToken cancellationToken)
    {
        var payload = MessagingJson.SerializeEnvelope("device_removed", removalEvent);
        var bytes = Encoding.UTF8.GetBytes(payload);
        var connections = _connectionManager.GetByUser(userId);

        foreach (var connection in connections)
        {
            if (connection.Socket.State != WebSocketState.Open)
                continue;

            await connection.Socket.SendAsync(new ArraySegment<byte>(bytes), WebSocketMessageType.Text, true, cancellationToken);
        }
    }
}
