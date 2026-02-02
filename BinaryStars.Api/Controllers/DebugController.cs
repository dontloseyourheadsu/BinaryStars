using Microsoft.AspNetCore.Mvc;
using System.IdentityModel.Tokens.Jwt;
using Microsoft.Extensions.Hosting;

namespace BinaryStars.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
public class DebugController : ControllerBase
{
    private readonly IHostEnvironment _env;

    public DebugController(IHostEnvironment env)
    {
        _env = env;
    }

    // Development-only helper to decode a JWT without validating it.
    // POST /api/debug/token  { "token": "<jwt>" }
    [HttpPost("token")]
    public IActionResult DecodeToken([FromBody] TokenRequest req)
    {
        if (!_env.IsDevelopment())
            return Forbid();

        if (string.IsNullOrWhiteSpace(req?.Token))
            return BadRequest("token required");

        try
        {
            var handler = new JwtSecurityTokenHandler();
            var jwt = handler.ReadJwtToken(req.Token);

            var payload = jwt.Claims.ToDictionary(c => c.Type, c => c.Value);
            var header = jwt.Header.ToDictionary(k => k.Key, k => k.Value?.ToString());

            return Ok(new { header, payload });
        }
        catch (Exception ex)
        {
            return BadRequest(new { error = ex.Message });
        }
    }

    public record TokenRequest(string Token);
}
