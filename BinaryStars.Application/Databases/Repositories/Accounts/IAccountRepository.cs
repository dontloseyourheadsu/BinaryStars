using BinaryStars.Application.Databases.DatabaseModels.Accounts;
using Microsoft.AspNetCore.Identity;

namespace BinaryStars.Application.Databases.Repositories.Accounts;

/// <summary>
/// Repository abstraction for account and identity operations.
/// </summary>
public interface IAccountRepository
{
    /// <summary>
    /// Creates a new identity user with the provided password.
    /// </summary>
    /// <param name="user">The user to create.</param>
    /// <param name="password">The plaintext password.</param>
    /// <returns>The identity result.</returns>
    Task<IdentityResult> CreateUserAsync(UserDbModel user, string password);

    /// <summary>
    /// Finds a user by email address.
    /// </summary>
    /// <param name="email">The email address.</param>
    /// <returns>The user, or null if not found.</returns>
    Task<UserDbModel?> FindByEmailAsync(string email);

    /// <summary>
    /// Finds a user by username.
    /// </summary>
    /// <param name="userName">The username.</param>
    /// <returns>The user, or null if not found.</returns>
    Task<UserDbModel?> FindByNameAsync(string userName);

    /// <summary>
    /// Finds a user by unique identifier.
    /// </summary>
    /// <param name="userId">The user identifier.</param>
    /// <returns>The user, or null if not found.</returns>
    Task<UserDbModel?> FindByIdAsync(Guid userId);

    /// <summary>
    /// Checks a user's password credentials.
    /// </summary>
    /// <param name="user">The user to validate.</param>
    /// <param name="password">The plaintext password.</param>
    /// <returns>The sign-in result.</returns>
    Task<SignInResult> CheckPasswordSignInAsync(UserDbModel user, string password);

    /// <summary>
    /// Gets the external login providers linked to a user.
    /// </summary>
    /// <param name="user">The user.</param>
    /// <returns>The list of login infos.</returns>
    Task<IList<UserLoginInfo>> GetLoginsAsync(UserDbModel user);

    /// <summary>
    /// Links an external login provider to a user.
    /// </summary>
    /// <param name="user">The user.</param>
    /// <param name="login">The external login data.</param>
    /// <returns>The identity result.</returns>
    Task<IdentityResult> AddLoginAsync(UserDbModel user, UserLoginInfo login);
}
