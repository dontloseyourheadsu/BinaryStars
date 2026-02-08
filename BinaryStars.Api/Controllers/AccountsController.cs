using System.Security.Claims;
using BinaryStars.Api.Models;
using BinaryStars.Application.Services.Accounts;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace BinaryStars.Api.Controllers;

/// <summary>
/// Provides account profile endpoints for authenticated users.
/// </summary>
[ApiController]
[Route("api/[controller]")]
[Authorize]
public class AccountsController : ControllerBase
{
    private readonly IAccountsReadService _accountsReadService;

    /// <summary>
    /// Initializes a new instance of the <see cref="AccountsController"/> class.
    /// </summary>
    /// <param name="accountsReadService">The account read service.</param>
    public AccountsController(IAccountsReadService accountsReadService)
    {
        _accountsReadService = accountsReadService;
    }

    /// <summary>
    /// Gets the profile of the currently authenticated user.
    /// </summary>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The account profile response.</returns>
    [HttpGet("me")]
    public async Task<IActionResult> GetProfile(CancellationToken cancellationToken)
    {
        var userIdClaim = User.FindFirstValue(ClaimTypes.NameIdentifier);
        if (string.IsNullOrWhiteSpace(userIdClaim) || !Guid.TryParse(userIdClaim, out var userId))
            return Unauthorized();

        var result = await _accountsReadService.GetProfileAsync(userId, cancellationToken);
        if (!result.IsSuccess)
            return BadRequest(result.Errors);

        var user = result.Value;
        return Ok(new AccountProfileResponse(user.Id, user.Username, user.Email, user.Role));
    }
}
