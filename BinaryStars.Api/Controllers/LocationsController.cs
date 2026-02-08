using BinaryStars.Application.Services.Locations;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
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
    private readonly ILocationHistoryReadService _readService;
    private readonly ILocationHistoryWriteService _writeService;

    /// <summary>
    /// Initializes a new instance of the <see cref="LocationsController"/> class.
    /// </summary>
    /// <param name="readService">The location read service.</param>
    /// <param name="writeService">The location write service.</param>
    public LocationsController(ILocationHistoryReadService readService, ILocationHistoryWriteService writeService)
    {
        _readService = readService;
        _writeService = writeService;
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
        var result = await _writeService.AddLocationAsync(userId, request, cancellationToken);

        if (result.IsSuccess)
            return Ok();

        return BadRequest(result.Errors);
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
            return Ok(result.Value);

        return BadRequest(result.Errors);
    }
}
