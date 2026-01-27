using BinaryStars.Application.Databases.DatabaseModels.Accounts;
using BinaryStars.Domain.Accounts.Users;

namespace BinaryStars.Application.Mappers.Accounts;

public static class AccountMapper
{
    public static User ToDomain(this UserDbModel model)
    {
        return new User(model.Id, model.UserName!, model.Email!, model.Role);
    }

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
