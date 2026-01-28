using BinaryStars.Application.Databases.DatabaseModels.Accounts;
using Microsoft.AspNetCore.Identity;

namespace BinaryStars.Application.Databases.Repositories.Accounts;

public class AccountRepository : IAccountRepository
{
    private readonly UserManager<UserDbModel> _userManager;
    private readonly SignInManager<UserDbModel> _signInManager;

    public AccountRepository(UserManager<UserDbModel> userManager, SignInManager<UserDbModel> signInManager)
    {
        _userManager = userManager;
        _signInManager = signInManager;
    }

    public Task<IdentityResult> CreateUserAsync(UserDbModel user, string password)
    {
        // UserManager does not support cancellation token on CreateAsync unfortunately in current default implementation,
        // but we should check if we can pass it, or just wrap it. 
        // Identity methods usually don't take CT in the core interfaces for some operations.
        // However, we should respect it where possible if EntityFramework stores are used directly.
        // Since we are using UserManager, we are bound by its API.
        return _userManager.CreateAsync(user, password);
    }

    public Task<UserDbModel?> FindByEmailAsync(string email)
    {
        return _userManager.FindByEmailAsync(email);
    }

    public Task<UserDbModel?> FindByNameAsync(string userName)
    {
        return _userManager.FindByNameAsync(userName);
    }

    public Task<UserDbModel?> FindByIdAsync(Guid userId)
    {
        return _userManager.FindByIdAsync(userId.ToString());
    }

    public Task<SignInResult> CheckPasswordSignInAsync(UserDbModel user, string password)
    {
        // LockoutOnFailure: false for now
        return _signInManager.CheckPasswordSignInAsync(user, password, lockoutOnFailure: false);
    }
}
