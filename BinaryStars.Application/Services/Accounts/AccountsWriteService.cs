using BinaryStars.Application.Databases.DatabaseModels.Accounts;
using BinaryStars.Application.Databases.Repositories.Accounts;
using BinaryStars.Application.Validators.Accounts;
using BinaryStars.Domain;
using BinaryStars.Domain.Accounts.Users;

namespace BinaryStars.Application.Services.Accounts;

public interface IAccountsWriteService
{
    Task<Result> RegisterAsync(RegisterRequest request, CancellationToken cancellationToken);
    Task<Result> LoginAsync(LoginRequest request, CancellationToken cancellationToken);
}

public class AccountsWriteService : IAccountsWriteService
{
    private readonly IAccountRepository _accountRepository;
    private readonly AuthValidator _validator;

    public AccountsWriteService(
        IAccountRepository accountRepository,
        AuthValidator validator)
    {
        _accountRepository = accountRepository;
        _validator = validator;
    }

    public async Task<Result> RegisterAsync(RegisterRequest request, CancellationToken cancellationToken)
    {
        var validationResult = _validator.ValidateRegister(request);
        if (!validationResult.IsSuccess)
            return validationResult;

        var user = new UserDbModel
        {
            UserName = request.Username,
            Email = request.Email,
            Role = UserRole.Free // Default role
        };

        var result = await _accountRepository.CreateUserAsync(user, request.Password);

        if (!result.Succeeded)
        {
            return Result.Failure(result.Errors.Select(e => e.Description).ToList());
        }

        return Result.Success();
    }

    public async Task<Result> LoginAsync(LoginRequest request, CancellationToken cancellationToken)
    {
        var validationResult = _validator.ValidateLogin(request);
        if (!validationResult.IsSuccess)
            return validationResult;

        var user = await _accountRepository.FindByEmailAsync(request.Email);
        if (user == null)
            return Result.Failure("Invalid login attempt.");

        var result = await _accountRepository.CheckPasswordSignInAsync(user, request.Password);
        if (result.Succeeded)
        {
            return Result.Success();
        }

        return Result.Failure("Invalid login attempt.");
    }
}
