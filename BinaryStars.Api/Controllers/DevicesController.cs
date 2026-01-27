using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using BinaryStars.Application.Services.Devices;
using System.Security.Claims;

namespace BinaryStars.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
[Authorize] // Ensure authentication is required
public class DevicesController : ControllerBase
{
    private readonly IDevicesReadService _devicesReadService;

    public DevicesController(IDevicesReadService devicesReadService)
    {
        _devicesReadService = devicesReadService;
    }

    [HttpGet]
    public async Task<IActionResult> GetDevices(CancellationToken cancellationToken)
    {
        // For now, using a mocked UserId since we rely on cookie auth or similar.
        // In real app, we extract userId from claims.
        // var userId = Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var userId = Guid.NewGuid();

        var result = await _devicesReadService.GetDevicesAsync(userId, cancellationToken);
        if (result.IsSuccess)
        {
            return Ok(result.Value);
        }
        return BadRequest(result.Errors);
    }
}
