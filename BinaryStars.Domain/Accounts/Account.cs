using BinaryStars.Domain.Accounts.Users;
using BinaryStars.Domain.Devices;
using BinaryStars.Domain.Errors.Accounts;

namespace BinaryStars.Domain.Accounts;

public readonly record struct Account
{
    public User User { get; init; }
    public IReadOnlyList<Device> Devices { get; init; }
    public int ConnectedDevicesCount => Devices?.Count ?? 0;

    public Account(User user, IReadOnlyList<Device> devices)
    {
        if (user == default) throw new ArgumentException(AccountErrors.UserCannotBeDefault, nameof(user));
        if (devices is null) throw new ArgumentException(AccountErrors.DevicesCannotBeNull, nameof(devices));

        User = user;
        Devices = devices;
    }
}
