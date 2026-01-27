using BinaryStars.Application.Databases.DatabaseModels.Accounts;
using Microsoft.AspNetCore.Identity;

namespace BinaryStars.Application.Databases.Repositories.Accounts;

public interface IAccountRepository
{
    Task<IdentityResult> CreateUserAsync(UserDbModel user, string password);
    Task<UserDbModel?> FindByEmailAsync(string email);
    Task<UserDbModel?> FindByIdAsync(Guid userId);
    Task<SignInResult> CheckPasswordSignInAsync(UserDbModel user, string password);
}
