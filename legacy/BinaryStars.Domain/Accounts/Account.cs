using BinaryStars.Domain.Accounts.Users;
using BinaryStars.Domain.Devices;
using BinaryStars.Domain.Errors.Accounts;

namespace BinaryStars.Domain.Accounts;

/// <summary>
/// Represents a user account paired with its registered devices.
/// </summary>
public readonly record struct Account
{
    /// <summary>
    /// Gets the account owner.
    /// </summary>
    public User User { get; init; }

    /// <summary>
    /// Gets the devices currently linked to the account.
    /// </summary>
    public IReadOnlyList<Device> Devices { get; init; }

    /// <summary>
    /// Gets the number of linked devices.
    /// </summary>
    public int ConnectedDevicesCount => Devices?.Count ?? 0;

    /// <summary>
    /// Initializes a new <see cref="Account"/> with the required user and devices.
    /// </summary>
    /// <param name="user">The account owner.</param>
    /// <param name="devices">The devices linked to the account.</param>
    /// <exception cref="ArgumentException">
    /// Thrown when <paramref name="user"/> is default or <paramref name="devices"/> is null.
    /// </exception>
    public Account(User user, IReadOnlyList<Device> devices)
    {
        if (user == default) throw new ArgumentException(AccountErrors.UserCannotBeDefault, nameof(user));
        if (devices is null) throw new ArgumentException(AccountErrors.DevicesCannotBeNull, nameof(devices));

        User = user;
        Devices = devices;
    }
}
