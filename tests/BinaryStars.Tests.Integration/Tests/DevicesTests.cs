using System.Net.Http.Json;
using BinaryStars.Tests.Integration.Fixtures;
using BinaryStars.Tests.Integration.Helpers;

namespace BinaryStars.Tests.Integration.Tests;

[Collection("integration")]
public class DevicesTests
{
    private readonly IntegrationTestFixture _fixture;

    public DevicesTests(IntegrationTestFixture fixture)
    {
        _fixture = fixture;
    }

    [Fact]
    public async Task RegisterDevice_ThenListIncludesDevice()
    {
        var client = _fixture.Factory.CreateClient();
        var suffix = Guid.NewGuid().ToString("N");
        var token = await AuthTestHelper.RegisterAndLoginAsync(
            client,
            $"device_user_{suffix}",
            $"device_user_{suffix}@example.com",
            "Password123!");

        client.SetBearerToken(token);

        var deviceId = $"device-{suffix}";
        var registerResponse = await client.PostAsJsonAsync("api/devices/register", new
        {
            id = deviceId,
            name = "Test Device",
            ipAddress = "127.0.0.1",
            ipv6Address = "::1",
            publicKey = "test-key",
            publicKeyAlgorithm = "RSA"
        });

        registerResponse.EnsureSuccessStatusCode();

        var listResponse = await client.GetAsync("api/devices");
        listResponse.EnsureSuccessStatusCode();

        var devices = await listResponse.Content.ReadFromJsonAsync<List<DeviceResponse>>(TestJson.Options);
        Assert.NotNull(devices);
        Assert.Contains(devices!, device => device.Id == deviceId);
    }

    private sealed record DeviceResponse(string Id, string Name);
}
