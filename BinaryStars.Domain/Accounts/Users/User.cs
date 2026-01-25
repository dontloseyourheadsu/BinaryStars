using BinaryStars.Domain.Errors.Accounts.Users;

namespace BinaryStars.Domain.Accounts.Users;

public readonly record struct User
{
    public Guid Id { get; init; }
    public string Username { get; init; }
    public string Email { get; init; }
    public UserRole Role { get; init; }

    public User(Guid id, string username, string email, UserRole role)
    {
        if (id == Guid.Empty) throw new ArgumentException(UserErrors.IdCannotBeEmpty, nameof(id));
        if (string.IsNullOrWhiteSpace(username)) throw new ArgumentException(UserErrors.UsernameCannotBeNullOrWhitespace, nameof(username));
        if (string.IsNullOrWhiteSpace(email)) throw new ArgumentException(UserErrors.EmailCannotBeNullOrWhitespace, nameof(email));

        Id = id;
        Username = username;
        Email = email;
        Role = role;
    }
}