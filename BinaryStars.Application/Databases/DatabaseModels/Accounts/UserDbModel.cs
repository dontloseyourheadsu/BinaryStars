using BinaryStars.Application.Databases.DatabaseModels.Devices;
using BinaryStars.Domain.Accounts.Users;
using Microsoft.AspNetCore.Identity;

namespace BinaryStars.Application.Databases.DatabaseModels.Accounts;

/// <summary>
/// Identity user entity extended with BinaryStars role information.
/// </summary>
public class UserDbModel : IdentityUser<Guid>
{
    /// <summary>
    /// Gets or sets the application-specific user role.
    /// </summary>
    public UserRole Role { get; set; }

    /// <summary>
    /// Gets or sets the devices linked to this user.
    /// </summary>
    public virtual ICollection<DeviceDbModel> Devices { get; set; } = new List<DeviceDbModel>();
}
