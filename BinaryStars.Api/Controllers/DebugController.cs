using Microsoft.AspNetCore.Mvc;
using System.IdentityModel.Tokens.Jwt;
using Microsoft.Extensions.Hosting;

namespace BinaryStars.Api.Controllers;

/// <summary>
/// Development-only debugging endpoints.
/// </summary>
[ApiController]
[Route("api/[controller]")]
public class DebugController : ControllerBase
{
    private readonly IHostEnvironment _env;

    /// <summary>
    /// Initializes a new instance of the <see cref="DebugController"/> class.
    /// </summary>
    /// <param name="env">The hosting environment.</param>
    public DebugController(IHostEnvironment env)
    {
        _env = env;
    }

    // Development-only helper to decode a JWT without validating it.
    // POST /api/debug/token  { "token": "<jwt>" }
    /// <summary>
    /// Decodes a JWT without validation (development only).
    /// </summary>
    /// <param name="req">The token request.</param>
    /// <returns>The decoded JWT header and payload.</returns>
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

    /// <summary>
    /// Request payload for decoding a JWT.
    /// </summary>
    /// <param name="Token">The JWT to decode.</param>
    public record TokenRequest(string Token);
}
