using BinaryStars.Application.Databases.Repositories.Accounts;
using BinaryStars.Application.Mappers.Accounts;
using BinaryStars.Domain;
using BinaryStars.Domain.Accounts.Users;

namespace BinaryStars.Application.Services.Accounts;

/// <summary>
/// Read-only account operations exposed by the application layer.
/// </summary>
public interface IAccountsReadService
{
    /// <summary>
    /// Gets the user profile for the specified user identifier.
    /// </summary>
    /// <param name="userId">The user identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The user profile or a failure result.</returns>
    Task<Result<User>> GetProfileAsync(Guid userId, CancellationToken cancellationToken);
}

/// <summary>
/// Application service for reading account data.
/// </summary>
public class AccountsReadService : IAccountsReadService
{
    private readonly IAccountRepository _accountRepository;

    /// <summary>
    /// Initializes a new instance of the <see cref="AccountsReadService"/> class.
    /// </summary>
    /// <param name="accountRepository">Repository for account data.</param>
    public AccountsReadService(IAccountRepository accountRepository)
    {
        _accountRepository = accountRepository;
    }

    /// <inheritdoc />
    public async Task<Result<User>> GetProfileAsync(Guid userId, CancellationToken cancellationToken)
    {
        // Identity implementation usually doesn't take CancellationToken for findById, but we pass it anyway to our repo if we were using DbContext directly
        var userModel = await _accountRepository.FindByIdAsync(userId);

        if (userModel == null)
            return Result<User>.Failure("User not found.");

        return Result<User>.Success(userModel.ToDomain());
    }
}
