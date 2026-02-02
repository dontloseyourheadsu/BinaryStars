using Microsoft.AspNetCore.Mvc;
using BinaryStars.Application.Services.Accounts;
using BinaryStars.Application.Validators.Accounts;
using BinaryStars.Api.Services;
using Microsoft.Extensions.Logging;

namespace BinaryStars.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
public class AuthController : ControllerBase
{
    private readonly IAccountsWriteService _accountsWriteService;
    private readonly JwtTokenService _tokenService;
    private readonly ILogger<AuthController> _logger;

    public AuthController(
        IAccountsWriteService accountsWriteService,
        JwtTokenService tokenService,
        ILogger<AuthController> logger)
    {
        _accountsWriteService = accountsWriteService;
        _tokenService = tokenService;
        _logger = logger;
    }

    [HttpPost("register")]
    public async Task<IActionResult> Register([FromBody] RegisterRequest request, CancellationToken cancellationToken)
    {
        var result = await _accountsWriteService.RegisterAsync(request, cancellationToken);
        if (result.IsSuccess)
        {
            var authResponse = _tokenService.CreateToken(result.Value);
            return Ok(authResponse);
        }
        return BadRequest(result.Errors);
    }

    [HttpPost("login")]
    public async Task<IActionResult> Login([FromBody] LoginRequest request, CancellationToken cancellationToken)
    {
        var result = await _accountsWriteService.LoginAsync(request, cancellationToken);
        if (result.IsSuccess)
        {
            var authResponse = _tokenService.CreateToken(result.Value);
            return Ok(authResponse);
        }
        return Unauthorized(result.Errors);
    }

    [HttpPost("login/external")]
    public async Task<IActionResult> ExternalLogin([FromBody] ExternalLoginRequest request, CancellationToken cancellationToken)
    {
        var result = await _accountsWriteService.ExternalLoginAsync(request, cancellationToken);
        if (result.IsSuccess)
        {
            var authResponse = _tokenService.CreateToken(result.Value);
            return Ok(authResponse);
        }

        // Log the failure reasons to help debug token validation / provider issues
        if (result.Errors != null && result.Errors.Any())
        {
            _logger.LogWarning("External login failed for provider={Provider}: {Errors}", request.Provider, string.Join("; ", result.Errors));
        }

        return Unauthorized(result.Errors);
    }
}
