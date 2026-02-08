using System.Net.Http.Json;
using System.Text.Json;
using BinaryStars.Tests.Integration.Fixtures;
using BinaryStars.Tests.Integration.Helpers;

namespace BinaryStars.Tests.Integration.Tests;

/// <summary>
/// Integration tests for file transfer creation.
/// </summary>
[Collection("integration")]
public class FileTransferTests
{
    private readonly IntegrationTestFixture _fixture;

    public FileTransferTests(IntegrationTestFixture fixture)
    {
        _fixture = fixture;
    }

    /// <summary>
    /// Creates a transfer and verifies it starts in a queued state.
    /// </summary>
    [Fact]
    public async Task CreateTransfer_ReturnsQueuedStatus()
    {
        var client = _fixture.Factory.CreateClient();
        var suffix = Guid.NewGuid().ToString("N");
        var token = await AuthTestHelper.RegisterAndLoginAsync(
            client,
            $"transfer_user_{suffix}",
            $"transfer_user_{suffix}@example.com",
            "Password123!");

        client.SetBearerToken(token);

        var senderId = $"sender-{suffix}";
        var targetId = $"target-{suffix}";

        await RegisterDeviceAsync(client, senderId, "Sender Device");
        await RegisterDeviceAsync(client, targetId, "Target Device");

        var createResponse = await client.PostAsJsonAsync("api/files/transfers", new
        {
            fileName = "sample.txt",
            contentType = "text/plain",
            sizeBytes = 12,
            senderDeviceId = senderId,
            targetDeviceId = targetId,
            encryptionEnvelope = "{}"
        });

        createResponse.EnsureSuccessStatusCode();

        using var payload = JsonDocument.Parse(await createResponse.Content.ReadAsStringAsync());
        var status = payload.RootElement.GetProperty("status").GetString();
        Assert.Equal("Queued", status);
    }

    /// <summary>
    /// Registers a device for test setup.
    /// </summary>
    private static async Task RegisterDeviceAsync(HttpClient client, string deviceId, string name)
    {
        var response = await client.PostAsJsonAsync("api/devices/register", new
        {
            id = deviceId,
            name,
            ipAddress = "127.0.0.1",
            ipv6Address = "::1",
            publicKey = "test-key",
            publicKeyAlgorithm = "RSA"
        });

        response.EnsureSuccessStatusCode();
    }
}
