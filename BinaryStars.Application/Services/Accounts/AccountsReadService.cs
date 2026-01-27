using BinaryStars.Application.Databases.Repositories.Accounts;
using BinaryStars.Application.Mappers.Accounts;
using BinaryStars.Domain;
using BinaryStars.Domain.Accounts.Users;

namespace BinaryStars.Application.Services.Accounts;

public interface IAccountsReadService
{
    Task<Result<User>> GetProfileAsync(Guid userId, CancellationToken cancellationToken);
}

public class AccountsReadService : IAccountsReadService
{
    private readonly IAccountRepository _accountRepository;

    public AccountsReadService(IAccountRepository accountRepository)
    {
        _accountRepository = accountRepository;
    }

    public async Task<Result<User>> GetProfileAsync(Guid userId, CancellationToken cancellationToken)
    {
        // Identity implementation usually doesn't take CancellationToken for findById, but we pass it anyway to our repo if we were using DbContext directly
        var userModel = await _accountRepository.FindByIdAsync(userId);

        if (userModel == null)
            return Result<User>.Failure("User not found.");

        return Result<User>.Success(userModel.ToDomain());
    }
}
