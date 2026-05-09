using BinaryStars.Domain.Errors.Accounts.Users;

namespace BinaryStars.Domain.Accounts.Users;

/// <summary>
/// Represents a user identity within the BinaryStars domain model.
/// </summary>
public readonly record struct User
{
    /// <summary>
    /// Gets the unique identifier for the user.
    /// </summary>
    public Guid Id { get; init; }

    /// <summary>
    /// Gets the display name chosen by the user.
    /// </summary>
    public string Username { get; init; }

    /// <summary>
    /// Gets the primary email address for the user.
    /// </summary>
    public string Email { get; init; }

    /// <summary>
    /// Gets the authorization role assigned to the user.
    /// </summary>
    public UserRole Role { get; init; }

    /// <summary>
    /// Initializes a new <see cref="User"/> with validated identifiers and role.
    /// </summary>
    /// <param name="id">The unique user identifier.</param>
    /// <param name="username">The user display name.</param>
    /// <param name="email">The user email address.</param>
    /// <param name="role">The role assigned to the user.</param>
    /// <exception cref="ArgumentException">
    /// Thrown when <paramref name="id"/>, <paramref name="username"/>, or <paramref name="email"/> is invalid.
    /// </exception>
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