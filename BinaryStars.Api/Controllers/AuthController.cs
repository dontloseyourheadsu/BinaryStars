using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Identity;
using BinaryStars.Application.Services.Accounts;
using BinaryStars.Application.Services.Devices;
using BinaryStars.Application.Validators.Accounts;
using BinaryStars.Application.Databases.DatabaseModels.Accounts;

namespace BinaryStars.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
public class AuthController : ControllerBase
{
    private readonly IAccountsWriteService _accountsWriteService;
    private readonly IAccountsReadService _accountsReadService;
    private readonly SignInManager<UserDbModel> _signInManager;

    public AuthController(
        IAccountsWriteService accountsWriteService,
        IAccountsReadService accountsReadService,
        SignInManager<UserDbModel> signInManager)
    {
        _accountsWriteService = accountsWriteService;
        _accountsReadService = accountsReadService;
        _signInManager = signInManager;
    }

    [HttpPost("register")]
    public async Task<IActionResult> Register([FromBody] RegisterRequest request, CancellationToken cancellationToken)
    {
        var result = await _accountsWriteService.RegisterAsync(request, cancellationToken);
        if (result.IsSuccess)
        {
            await _signInManager.SignInAsync(result.Value, isPersistent: true);
            return Ok();
        }
        return BadRequest(result.Errors);
    }

    [HttpPost("login")]
    public async Task<IActionResult> Login([FromBody] LoginRequest request, CancellationToken cancellationToken)
    {
        var result = await _accountsWriteService.LoginAsync(request, cancellationToken);
        if (result.IsSuccess)
        {
            await _signInManager.SignInAsync(result.Value, isPersistent: true);
            return Ok();
        }
        return Unauthorized(result.Errors);
    }

    [HttpPost("login/external")]
    public async Task<IActionResult> ExternalLogin([FromBody] ExternalLoginRequest request, CancellationToken cancellationToken)
    {
        var result = await _accountsWriteService.ExternalLoginAsync(request, cancellationToken);
        if (result.IsSuccess)
        {
            await _signInManager.SignInAsync(result.Value, isPersistent: true);
            return Ok();
        }

        return Unauthorized(result.Errors);
    }
}
