using BinaryStars.Application.Services.Locations;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using System.Security.Claims;

namespace BinaryStars.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
[Authorize]
public class LocationsController : ControllerBase
{
    private readonly ILocationHistoryReadService _readService;
    private readonly ILocationHistoryWriteService _writeService;

    public LocationsController(ILocationHistoryReadService readService, ILocationHistoryWriteService writeService)
    {
        _readService = readService;
        _writeService = writeService;
    }

    [HttpPost]
    public async Task<IActionResult> CreateLocation([FromBody] LocationUpdateRequest request, CancellationToken cancellationToken)
    {
        var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var result = await _writeService.AddLocationAsync(userId, request, cancellationToken);

        if (result.IsSuccess)
            return Ok();

        return BadRequest(result.Errors);
    }

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
