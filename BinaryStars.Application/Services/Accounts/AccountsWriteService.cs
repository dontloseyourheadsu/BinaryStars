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
    Task<Result> ExternalLoginAsync(ExternalLoginRequest request, CancellationToken cancellationToken);
}

public class AccountsWriteService : IAccountsWriteService
{
    private readonly IAccountRepository _accountRepository;
    private readonly AuthValidator _validator;
    private readonly ExternalIdentityValidator _identityValidator;

    public AccountsWriteService(
        IAccountRepository accountRepository,
        AuthValidator validator,
        ExternalIdentityValidator identityValidator)
    {
        _accountRepository = accountRepository;
        _validator = validator;
        _identityValidator = identityValidator;
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

    public async Task<Result> ExternalLoginAsync(ExternalLoginRequest request, CancellationToken cancellationToken)
    {
        var validationResult = _validator.ValidateExternal(request);
        if (!validationResult.IsSuccess)
            return validationResult;

        var externalResult = await _identityValidator.ValidateAsync(request, cancellationToken);
        if (!externalResult.IsSuccess || string.IsNullOrWhiteSpace(externalResult.Email))
            return Result.Failure(externalResult.Error ?? "External token validation failed.");

        var user = await _accountRepository.FindByEmailAsync(externalResult.Email);
        if (user == null)
        {
            user = new UserDbModel
            {
                UserName = request.Username,
                Email = externalResult.Email,
                Role = UserRole.Free
            };

            var tempPassword = $"Ext!{Guid.NewGuid():N}aA1"; // satisfies basic complexity
            var createResult = await _accountRepository.CreateUserAsync(user, tempPassword);
            if (!createResult.Succeeded)
            {
                return Result.Failure(createResult.Errors.Select(e => e.Description).ToList());
            }
        }

        return Result.Success();
    }
}
