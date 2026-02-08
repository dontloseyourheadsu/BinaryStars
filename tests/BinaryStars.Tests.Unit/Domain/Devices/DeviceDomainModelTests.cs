using BinaryStars.Domain.Devices;
using BinaryStars.Domain.Errors.Devices;

namespace BinaryStars.Tests.Unit.Domain.Devices;

/// <summary>
/// Unit tests for the <see cref="Device"/> domain model.
/// </summary>
public class DeviceDomainModelTests
{
    /// <summary>
    /// Verifies devices can be created with valid parameters.
    /// </summary>
    [Fact]
    public void CreatingDevice_WithValidParameters_ShouldSucceed()
    {
        // Arrange
        var id = "device-123";
        var name = "My Device";
        var type = DeviceType.Linux;
        var ipAddress = "192.168.1.100";
        var batteryLevel = 85;
        var isOnline = true;
        var isSynced = true;
        var wifiUploadSpeed = "100 Mbps";
        var wifiDownloadSpeed = "200 Mbps";
        var lastSeen = DateTimeOffset.UtcNow;

        // Act
        var device = new Device(
            id,
            name,
            type,
            ipAddress,
            null, // ipv6Address
            batteryLevel,
            isOnline,
            isSynced,
            wifiUploadSpeed,
            wifiDownloadSpeed,
            lastSeen);

        // Assert
        Assert.Equal(id, device.Id);
        Assert.Equal(name, device.Name);
        Assert.Equal(type, device.Type);
        Assert.Equal(ipAddress, device.IpAddress);
        Assert.Equal(batteryLevel, device.BatteryLevel);
        Assert.Equal(isOnline, device.IsOnline);
        Assert.Equal(isSynced, device.IsSynced);
        Assert.Equal(wifiUploadSpeed, device.WifiUploadSpeed);
        Assert.Equal(wifiDownloadSpeed, device.WifiDownloadSpeed);
        Assert.Equal(lastSeen, device.LastSeen);
    }

    /// <summary>
    /// Verifies invalid parameters throw argument exceptions.
    /// </summary>
    [Theory]
    [InlineData(null, "My Device", "192.168.1.100", DeviceErrors.IdCannotBeNullOrWhitespace)]
    [InlineData("", "My Device", "192.168.1.100", DeviceErrors.IdCannotBeNullOrWhitespace)]
    [InlineData("   ", "My Device", "192.168.1.100", DeviceErrors.IdCannotBeNullOrWhitespace)]
    [InlineData("device-123", null, "192.168.1.100", DeviceErrors.NameCannotBeNullOrWhitespace)]
    [InlineData("device-123", "", "192.168.1.100", DeviceErrors.NameCannotBeNullOrWhitespace)]
    [InlineData("device-123", "   ", "192.168.1.100", DeviceErrors.NameCannotBeNullOrWhitespace)]
    [InlineData("device-123", "My Device", null, DeviceErrors.IpAddressCannotBeNullOrWhitespace)]
    [InlineData("device-123", "My Device", "", DeviceErrors.IpAddressCannotBeNullOrWhitespace)]
    [InlineData("device-123", "My Device", "   ", DeviceErrors.IpAddressCannotBeNullOrWhitespace)]
    public void CreatingDevice_WithInvalidParameters_ShouldThrowArgumentException(
        string? id,
        string? name,
        string? ipAddress,
        string expectedErrorMessage)
    {
        // Arrange
        var type = DeviceType.Linux;
        var batteryLevel = 85;
        var isOnline = true;
        var isSynced = true;
        var wifiUploadSpeed = "100 Mbps";
        var wifiDownloadSpeed = "200 Mbps";
        var lastSeen = DateTimeOffset.UtcNow;

        // Act & Assert
        var exception = Assert.Throws<ArgumentException>(() => new Device(
            id!,
            name!,
            type,
            ipAddress!,
            null, // ipv6Address
            batteryLevel,
            isOnline,
            isSynced,
            wifiUploadSpeed,
            wifiDownloadSpeed,
            lastSeen));

        Assert.Contains(expectedErrorMessage, exception.Message);
    }
}
