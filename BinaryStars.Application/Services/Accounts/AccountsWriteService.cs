using BinaryStars.Application.Databases.DatabaseModels.Accounts;
using BinaryStars.Application.Databases.Repositories.Accounts;
using BinaryStars.Application.Validators.Accounts;
using BinaryStars.Domain;
using BinaryStars.Domain.Accounts.Users;
using Microsoft.AspNetCore.Identity;

namespace BinaryStars.Application.Services.Accounts;

public interface IAccountsWriteService
{
    Task<Result<UserDbModel>> RegisterAsync(RegisterRequest request, CancellationToken cancellationToken);
    Task<Result<UserDbModel>> LoginAsync(LoginRequest request, CancellationToken cancellationToken);
    Task<Result<UserDbModel>> ExternalLoginAsync(ExternalLoginRequest request, CancellationToken cancellationToken);
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

    public async Task<Result<UserDbModel>> RegisterAsync(RegisterRequest request, CancellationToken cancellationToken)
    {
        var validationResult = _validator.ValidateRegister(request);
        if (!validationResult.IsSuccess)
            return Result<UserDbModel>.Failure(validationResult.Errors);

        var user = new UserDbModel
        {
            UserName = request.Username,
            Email = request.Email,
            Role = UserRole.Free // Default role
        };

        var result = await _accountRepository.CreateUserAsync(user, request.Password);

        if (!result.Succeeded)
        {
            return Result<UserDbModel>.Failure(result.Errors.Select(e => e.Description).ToList());
        }

        return Result<UserDbModel>.Success(user);
    }

    public async Task<Result<UserDbModel>> LoginAsync(LoginRequest request, CancellationToken cancellationToken)
    {
        var validationResult = _validator.ValidateLogin(request);
        if (!validationResult.IsSuccess)
            return Result<UserDbModel>.Failure(validationResult.Errors);

        UserDbModel? user;
        if (request.Email.Contains('@'))
        {
            user = await _accountRepository.FindByEmailAsync(request.Email);
        }
        else
        {
            user = await _accountRepository.FindByNameAsync(request.Email);
        }

        if (user == null)
            return Result<UserDbModel>.Failure("Invalid login attempt.");

        // Enforce Single Auth Provider: If user has external logins, disallow password login.
        var logins = await _accountRepository.GetLoginsAsync(user);
        if (logins.Any())
        {
            return Result<UserDbModel>.Failure($"Please login with {logins.First().LoginProvider}.");
        }

        var result = await _accountRepository.CheckPasswordSignInAsync(user, request.Password);
        if (result.Succeeded)
        {
            return Result<UserDbModel>.Success(user);
        }

        return Result<UserDbModel>.Failure("Invalid login attempt.");
    }

    public async Task<Result<UserDbModel>> ExternalLoginAsync(ExternalLoginRequest request, CancellationToken cancellationToken)
    {
        var validationResult = _validator.ValidateExternal(request);
        if (!validationResult.IsSuccess)
            return Result<UserDbModel>.Failure(validationResult.Errors);

        var externalResult = await _identityValidator.ValidateAsync(request, cancellationToken);
        if (!externalResult.IsSuccess || string.IsNullOrWhiteSpace(externalResult.Email))
            return Result<UserDbModel>.Failure(externalResult.Error ?? "External token validation failed.");

        var user = await _accountRepository.FindByEmailAsync(externalResult.Email);
        if (user != null)
        {
            // Enforce Single Auth Provider
            var logins = await _accountRepository.GetLoginsAsync(user);
            if (logins.Any())
            {
                if (!logins.Any(l => l.LoginProvider.Equals(request.Provider, StringComparison.OrdinalIgnoreCase)))
                {
                    return Result<UserDbModel>.Failure($"Please login with {logins.First().LoginProvider}.");
                }
            }
            else
            {
                // User exists but has no external logins -> Password user
                return Result<UserDbModel>.Failure("Please login with Password.");
            }
        }
        else
        {
            if (string.IsNullOrWhiteSpace(request.Username))
            {
                // New user, but username not provided. Client must prompt for username.
                return Result<UserDbModel>.Failure("User not found. Registration required.");
            }

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
                return Result<UserDbModel>.Failure(createResult.Errors.Select(e => e.Description).ToList());
            }

            // Link the external login
            if (!string.IsNullOrEmpty(externalResult.ProviderSubject))
            {
                var addLoginResult = await _accountRepository.AddLoginAsync(user, new UserLoginInfo(request.Provider, externalResult.ProviderSubject, request.Provider));
                if (!addLoginResult.Succeeded)
                {
                    return Result<UserDbModel>.Failure("Failed to link external login.");
                }
            }
        }

        return Result<UserDbModel>.Success(user);
    }
}

