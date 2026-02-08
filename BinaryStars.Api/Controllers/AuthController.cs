using Microsoft.AspNetCore.Mvc;
using BinaryStars.Application.Services.Accounts;
using BinaryStars.Application.Validators.Accounts;
using BinaryStars.Api.Services;
using Microsoft.Extensions.Logging;

namespace BinaryStars.Api.Controllers;

/// <summary>
/// Provides registration and authentication endpoints.
/// </summary>
[ApiController]
[Route("api/[controller]")]
public class AuthController : ControllerBase
{
    private readonly IAccountsWriteService _accountsWriteService;
    private readonly JwtTokenService _tokenService;
    private readonly ILogger<AuthController> _logger;

    /// <summary>
    /// Initializes a new instance of the <see cref="AuthController"/> class.
    /// </summary>
    /// <param name="accountsWriteService">The account write service.</param>
    /// <param name="tokenService">The JWT token service.</param>
    /// <param name="logger">The logger.</param>
    public AuthController(
        IAccountsWriteService accountsWriteService,
        JwtTokenService tokenService,
        ILogger<AuthController> logger)
    {
        _accountsWriteService = accountsWriteService;
        _tokenService = tokenService;
        _logger = logger;
    }

    /// <summary>
    /// Registers a new user and returns an access token.
    /// </summary>
    /// <param name="request">The registration request.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>An authentication response on success.</returns>
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

    /// <summary>
    /// Logs in a user and returns an access token.
    /// </summary>
    /// <param name="request">The login request.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>An authentication response on success.</returns>
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

    /// <summary>
    /// Logs in a user with an external identity provider.
    /// </summary>
    /// <param name="request">The external login request.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>An authentication response on success.</returns>
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
