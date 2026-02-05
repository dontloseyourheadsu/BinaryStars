using System.Net.Http.Json;
using System.Text.Json;
using BinaryStars.Api.Services;
using BinaryStars.Application.Databases.Repositories.Transfers;
using BinaryStars.Domain.Transfers;
using BinaryStars.Tests.Integration.Fixtures;
using BinaryStars.Tests.Integration.Helpers;
using Microsoft.Extensions.DependencyInjection;

namespace BinaryStars.Tests.Integration.Tests;

[Collection("integration")]
public class FileTransferJobTests
{
    private readonly IntegrationTestFixture _fixture;

    public FileTransferJobTests(IntegrationTestFixture fixture)
    {
        _fixture = fixture;
    }

    [Fact]
    public async Task SendToKafkaAsync_PersistsOffsetsAndMarksAvailable()
    {
        var client = _fixture.Factory.CreateClient();
        var suffix = Guid.NewGuid().ToString("N");
        var token = await AuthTestHelper.RegisterAndLoginAsync(
            client,
            $"job_user_{suffix}",
            $"job_user_{suffix}@example.com",
            "Password123!");

        client.SetBearerToken(token);

        var senderId = $"sender-{suffix}";
        var targetId = $"target-{suffix}";

        await RegisterDeviceAsync(client, senderId, "Sender Device");
        await RegisterDeviceAsync(client, targetId, "Target Device");

        var payloadBytes = "test-data"u8.ToArray();
        var createResponse = await client.PostAsJsonAsync("api/files/transfers", new
        {
            fileName = "job.txt",
            contentType = "text/plain",
            sizeBytes = payloadBytes.Length,
            senderDeviceId = senderId,
            targetDeviceId = targetId,
            encryptionEnvelope = "{}"
        });

        createResponse.EnsureSuccessStatusCode();

        using var payload = JsonDocument.Parse(await createResponse.Content.ReadAsStringAsync());
        var transferId = payload.RootElement.GetProperty("id").GetGuid();
        var tempFilePath = Path.Combine(_fixture.TempPath, $"{transferId:D}.bin");
        await File.WriteAllBytesAsync(tempFilePath, payloadBytes);

        using var scope = _fixture.Services.CreateScope();
        var job = scope.ServiceProvider.GetRequiredService<FileTransferJob>();
        var repository = scope.ServiceProvider.GetRequiredService<IFileTransferRepository>();

        await job.SendToKafkaAsync(new FileTransferJobRequest(transferId, tempFilePath, "Scram", null));

        var transfer = await repository.GetByIdAsync(transferId, CancellationToken.None);
        Assert.NotNull(transfer);
        Assert.Equal(FileTransferStatus.Available, transfer!.Status);
        Assert.True(transfer.PacketCount > 0);
        Assert.NotNull(transfer.KafkaStartOffset);
        Assert.NotNull(transfer.KafkaEndOffset);
        Assert.False(File.Exists(tempFilePath));
    }

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
