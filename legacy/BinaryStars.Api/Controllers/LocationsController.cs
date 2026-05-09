using Microsoft.Extensions.Logging;
using BinaryStars.Application.Services.Locations;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using System.Net.WebSockets;
using System.Text;
using BinaryStars.Api.Models;
using BinaryStars.Api.Services;
using System.Security.Claims;

namespace BinaryStars.Api.Controllers;

/// <summary>
/// Provides location history endpoints.
/// </summary>
[ApiController]
[Route("api/[controller]")]
[Authorize]
public class LocationsController : ControllerBase
{
    private readonly ILogger<LocationsController> _logger;

    private readonly ILocationHistoryReadService _readService;
    private readonly ILocationHistoryWriteService _writeService;
    private readonly MessagingConnectionManager _connectionManager;
    private readonly DeviceLiveLocationCache _liveLocationCache;

    /// <summary>
    /// Initializes a new instance of the <see cref="LocationsController"/> class.
    /// </summary>
    /// <param name="readService">The location read service.</param>
    /// <param name="writeService">The location write service.</param>
    public LocationsController(
        ILocationHistoryReadService readService,
        ILocationHistoryWriteService writeService,
        MessagingConnectionManager connectionManager,
        DeviceLiveLocationCache liveLocationCache, ILogger<LocationsController> logger)
    {
        _logger = logger;

        _readService = readService;
        _writeService = writeService;
        _connectionManager = connectionManager;
        _liveLocationCache = liveLocationCache;
    }

    /// <summary>
    /// Adds a new location update for a device.
    /// </summary>
    /// <param name="request">The location update request.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>Ok on success.</returns>
    [HttpPost]
    public async Task<IActionResult> CreateLocation([FromBody] LocationUpdateRequest request, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var utcRequest = request with { RecordedAt = request.RecordedAt.ToUniversalTime() };
        var result = await _writeService.AddLocationAsync(userId, utcRequest, cancellationToken, persistToHistory: true);

        if (result.IsSuccess)
        {
            var locationEvent = new LocationUpdateEvent(
                Guid.NewGuid().ToString("D"),
                userId,
                request.DeviceId,
                request.Latitude,
                request.Longitude,
                request.AccuracyMeters,
                utcRequest.RecordedAt,
                DateTimeOffset.UtcNow);

            _liveLocationCache.Upsert(locationEvent);
            await BroadcastLocationUpdateAsync(locationEvent, cancellationToken);
            return Ok();
        }

        return BadRequest(result.Errors);
    }

    /// <summary>
    /// Broadcasts a live location update without persisting it to history.
    /// </summary>
    /// <param name="request">The location update request.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>Ok on success.</returns>
    [HttpPost("live")]
    public async Task<IActionResult> CreateLiveLocation([FromBody] LocationUpdateRequest request, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var utcRequest = request with { RecordedAt = request.RecordedAt.ToUniversalTime() };
        var result = await _writeService.AddLocationAsync(userId, utcRequest, cancellationToken, persistToHistory: false);

        if (result.IsSuccess)
        {
            var locationEvent = new LocationUpdateEvent(
                Guid.NewGuid().ToString("D"),
                userId,
                request.DeviceId,
                request.Latitude,
                request.Longitude,
                request.AccuracyMeters,
                utcRequest.RecordedAt,
                DateTimeOffset.UtcNow);

            _liveLocationCache.Upsert(locationEvent);
            await BroadcastLocationUpdateAsync(locationEvent, cancellationToken);
            return Ok();
        }

        return BadRequest(result.Errors);
    }

    private async Task BroadcastLocationUpdateAsync(LocationUpdateEvent locationEvent, CancellationToken cancellationToken)
    {
        var payload = MessagingJson.SerializeEnvelope("location_update", locationEvent);
        var bytes = Encoding.UTF8.GetBytes(payload);
        var openConnections = _connectionManager
            .GetByUser(locationEvent.UserId)
            .Where(connection => connection.Socket.State == WebSocketState.Open)
            .ToList();

        foreach (var connection in openConnections)
        {
            await connection.Socket.SendAsync(new ArraySegment<byte>(bytes), WebSocketMessageType.Text, true, cancellationToken);
        }
    }

    /// <summary>
    /// Gets location history for a device.
    /// </summary>
    /// <param name="deviceId">The device identifier.</param>
    /// <param name="limit">The maximum number of results.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The list of location history points.</returns>
    [HttpGet("history")]
    public async Task<IActionResult> GetHistory([FromQuery] string deviceId, [FromQuery] int limit, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var result = await _readService.GetHistoryAsync(userId, deviceId, limit, cancellationToken);

        if (result.IsSuccess)
        {
            var history = result.Value;
            if (_liveLocationCache.TryGet(userId, deviceId, out var liveEvent) && liveEvent != null)
            {
                var hasMatchingTop = history.Count > 0 &&
                    history[0].RecordedAt == liveEvent.RecordedAt &&
                    Math.Abs(history[0].Latitude - liveEvent.Latitude) < 0.0000001 &&
                    Math.Abs(history[0].Longitude - liveEvent.Longitude) < 0.0000001;

                if (hasMatchingTop)
                {
                    history[0] = history[0] with { Title = "Live" };
                }
                else
                {
                    history.Insert(0, new LocationHistoryPointResponse(
                        Guid.NewGuid(),
                        "Live",
                        liveEvent.RecordedAt,
                        liveEvent.Latitude,
                        liveEvent.Longitude));
                }
            }

            return Ok(history);
        }

        return BadRequest(result.Errors);
    }
}
