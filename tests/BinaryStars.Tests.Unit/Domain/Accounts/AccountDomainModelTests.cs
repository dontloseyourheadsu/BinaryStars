using BinaryStars.Domain.Accounts;
using BinaryStars.Domain.Accounts.Users;
using BinaryStars.Domain.Devices;
using BinaryStars.Domain.Errors.Accounts;

namespace BinaryStars.Tests.Unit.Domain.Accounts;

public class AccountDomainModelTests
{
    [Fact]
    public void CreatingAccount_WithValidParameters_ShouldSucceed()
    {
        // Arrange
        var user = new User(Guid.NewGuid(), "testuser", "test@example.com", UserRole.Free);
        var devices = new List<Device>
        {
            new Device("d1", "Phone", DeviceType.Android, "10.0.0.1", null, 100, true, true, "10M", "10M", DateTimeOffset.UtcNow),
            new Device("d2", "Laptop", DeviceType.Linux, "10.0.0.2", null, 90, true, true, "50M", "50M", DateTimeOffset.UtcNow)
        };

        // Act
        var account = new Account(user, devices);

        // Assert
        Assert.Equal(user, account.User);
        Assert.Equal(devices, account.Devices);
        Assert.Equal(2, account.ConnectedDevicesCount);
    }

    [Fact]
    public void CreatingAccount_WithInvalidUser_ShouldThrowArgumentException()
    {
        // Arrange
        User user = default;
        var devices = new List<Device>();

        // Act & Assert
        var exception = Assert.Throws<ArgumentException>(() => new Account(user, devices));
        Assert.Contains(AccountErrors.UserCannotBeDefault, exception.Message);
    }

    [Fact]
    public void CreatingAccount_WithNullDevices_ShouldThrowArgumentException()
    {
        // Arrange
        var user = new User(Guid.NewGuid(), "testuser", "test@example.com", UserRole.Free);
        List<Device>? devices = null;

        // Act & Assert
        var exception = Assert.Throws<ArgumentException>(() => new Account(user, devices!));
        Assert.Contains(AccountErrors.DevicesCannotBeNull, exception.Message);
    }
}
