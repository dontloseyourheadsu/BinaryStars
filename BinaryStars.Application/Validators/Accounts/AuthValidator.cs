using BinaryStars.Domain;

namespace BinaryStars.Application.Validators.Accounts;

public record RegisterRequest(string Username, string Email, string Password);
public record LoginRequest(string Email, string Password);

public class AuthValidator
{
    public Result ValidateRegister(RegisterRequest request)
    {
        var errors = new List<string>();

        if (string.IsNullOrWhiteSpace(request.Username))
            errors.Add("Username is required.");

        if (string.IsNullOrWhiteSpace(request.Email))
            errors.Add("Email is required.");

        if (string.IsNullOrWhiteSpace(request.Password))
            errors.Add("Password is required.");

        return errors.Count > 0 ? Result.Failure(errors) : Result.Success();
    }

    public Result ValidateLogin(LoginRequest request)
    {
        var errors = new List<string>();

        if (string.IsNullOrWhiteSpace(request.Email))
            errors.Add("Email is required.");

        if (string.IsNullOrWhiteSpace(request.Password))
            errors.Add("Password is required.");

        return errors.Count > 0 ? Result.Failure(errors) : Result.Success();
    }
}
