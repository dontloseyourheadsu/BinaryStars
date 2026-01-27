using BinaryStars.Application.Databases.DatabaseModels.Devices;
using BinaryStars.Domain.Accounts.Users;
using Microsoft.AspNetCore.Identity;

namespace BinaryStars.Application.Databases.DatabaseModels.Accounts;

public class UserDbModel : IdentityUser<Guid>
{
    public UserRole Role { get; set; }

    // Navigation property for the Account relationship
    public virtual ICollection<DeviceDbModel> Devices { get; set; } = new List<DeviceDbModel>();
}
