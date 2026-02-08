using BinaryStars.Application.Databases.DatabaseModels.Accounts;
using Microsoft.AspNetCore.Identity;

namespace BinaryStars.Application.Databases.Repositories.Accounts;

/// <summary>
/// Identity-based repository for account operations.
/// </summary>
public class AccountRepository : IAccountRepository
{
    private readonly UserManager<UserDbModel> _userManager;
    private readonly SignInManager<UserDbModel> _signInManager;

    /// <summary>
    /// Initializes a new instance of the <see cref="AccountRepository"/> class.
    /// </summary>
    /// <param name="userManager">The user manager.</param>
    /// <param name="signInManager">The sign-in manager.</param>
    public AccountRepository(UserManager<UserDbModel> userManager, SignInManager<UserDbModel> signInManager)
    {
        _userManager = userManager;
        _signInManager = signInManager;
    }

    /// <inheritdoc />
    public Task<IdentityResult> CreateUserAsync(UserDbModel user, string password)
    {
        // UserManager does not support cancellation token on CreateAsync unfortunately in current default implementation,
        // but we should check if we can pass it, or just wrap it. 
        // Identity methods usually don't take CT in the core interfaces for some operations.
        // However, we should respect it where possible if EntityFramework stores are used directly.
        // Since we are using UserManager, we are bound by its API.
        return _userManager.CreateAsync(user, password);
    }

    /// <inheritdoc />
    public Task<UserDbModel?> FindByEmailAsync(string email)
    {
        return _userManager.FindByEmailAsync(email);
    }

    /// <inheritdoc />
    public Task<UserDbModel?> FindByNameAsync(string userName)
    {
        return _userManager.FindByNameAsync(userName);
    }

    /// <inheritdoc />
    public Task<UserDbModel?> FindByIdAsync(Guid userId)
    {
        return _userManager.FindByIdAsync(userId.ToString());
    }

    /// <inheritdoc />
    public Task<SignInResult> CheckPasswordSignInAsync(UserDbModel user, string password)
    {
        // LockoutOnFailure: false for now
        return _signInManager.CheckPasswordSignInAsync(user, password, lockoutOnFailure: false);
    }

    /// <inheritdoc />
    public Task<IList<UserLoginInfo>> GetLoginsAsync(UserDbModel user)
    {
        return _userManager.GetLoginsAsync(user);
    }

    /// <inheritdoc />
    public Task<IdentityResult> AddLoginAsync(UserDbModel user, UserLoginInfo login)
    {
        return _userManager.AddLoginAsync(user, login);
    }
}
