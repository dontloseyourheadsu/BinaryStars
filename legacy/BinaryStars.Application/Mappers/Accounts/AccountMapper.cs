using BinaryStars.Application.Databases.DatabaseModels.Accounts;
using BinaryStars.Domain.Accounts.Users;

namespace BinaryStars.Application.Mappers.Accounts;

/// <summary>
/// Maps account-related database models to domain models and back.
/// </summary>
public static class AccountMapper
{
    /// <summary>
    /// Converts a database user model into the domain user model.
    /// </summary>
    /// <param name="model">The database model.</param>
    /// <returns>The domain model.</returns>
    public static User ToDomain(this UserDbModel model)
    {
        return new User(model.Id, model.UserName!, model.Email!, model.Role);
    }

    /// <summary>
    /// Converts a domain user model into the database model.
    /// </summary>
    /// <param name="domain">The domain model.</param>
    /// <returns>The database model.</returns>
    public static UserDbModel ToDb(this User domain)
    {
        return new UserDbModel
        {
            Id = domain.Id,
            UserName = domain.Username,
            Email = domain.Email,
            Role = domain.Role
        };
    }
}
