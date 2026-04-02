using Microsoft.Extensions.Logging;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using BinaryStars.Application.Services.Devices;
using System.Security.Claims;
using BinaryStars.Api.Models;
using BinaryStars.Api.Services;
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
    private readonly ILogger<DevicesController> _logger;

    private readonly IDevicesReadService _devicesReadService;
    private readonly IDevicesWriteService _devicesWriteService;
    private readonly MessagingKafkaService _messagingKafkaService;
    private readonly MessagingConnectionManager _connectionManager;

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
        ILogger<DevicesController> logger)
    {
        _logger = logger;

        _devicesReadService = devicesReadService;
        _devicesWriteService = devicesWriteService;
        _messagingKafkaService = messagingKafkaService;
        _connectionManager = connectionManager;
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

            const KafkaAuthMode authMode = KafkaAuthMode.Scram;
            await _messagingKafkaService.PublishDeviceRemovedAsync(removalEvent, authMode, null, cancellationToken);

            await NotifyConnectedDevicesAsync(userId, removalEvent, cancellationToken);
            return Ok();
        }

        return BadRequest(result.Errors);
    }

    /// <summary>
    /// Updates telemetry and availability for a linked device.
    /// </summary>
    /// <param name="deviceId">The device identifier.</param>
    /// <param name="request">Telemetry payload.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The updated device.</returns>
    [HttpPut("{deviceId}/telemetry")]
    public async Task<IActionResult> UpdateTelemetry(string deviceId, [FromBody] UpdateDeviceTelemetryRequest request, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var result = await _devicesWriteService.UpdateTelemetryAsync(userId, deviceId, request, cancellationToken);

        if (result.IsSuccess)
        {
            await NotifyPresenceChangedAsync(userId, result.Value, cancellationToken);
            return Ok(result.Value);
        }

        return BadRequest(result.Errors);
    }

    /// <summary>
    /// Records a heartbeat for a linked device and marks it online.
    /// </summary>
    /// <param name="deviceId">The device identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The updated device.</returns>
    [HttpPost("{deviceId}/heartbeat")]
    public async Task<IActionResult> Heartbeat(string deviceId, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var result = await _devicesWriteService.HeartbeatAsync(userId, deviceId, cancellationToken);

        if (result.IsSuccess)
        {
            await NotifyPresenceChangedAsync(userId, result.Value, cancellationToken);
            return Ok(result.Value);
        }

        return BadRequest(result.Errors);
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

    private async Task NotifyPresenceChangedAsync(Guid userId, BinaryStars.Domain.Devices.Device device, CancellationToken cancellationToken)
    {
        var presenceEvent = new DevicePresenceEvent(
            Guid.NewGuid().ToString("D"),
            userId,
            device.Id,
            device.IsOnline,
            device.LastSeen,
            DateTimeOffset.UtcNow);

        var payload = MessagingJson.SerializeEnvelope("device_presence", presenceEvent);
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
