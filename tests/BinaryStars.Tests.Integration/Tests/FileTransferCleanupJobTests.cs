using BinaryStars.Api.Services;
using BinaryStars.Application.Databases.DatabaseModels.Transfers;
using BinaryStars.Application.Databases.Repositories.Transfers;
using BinaryStars.Domain.Transfers;
using BinaryStars.Tests.Integration.Fixtures;
using Microsoft.Extensions.DependencyInjection;

namespace BinaryStars.Tests.Integration.Tests;

[Collection("integration")]
public class FileTransferCleanupJobTests
{
    private readonly IntegrationTestFixture _fixture;

    public FileTransferCleanupJobTests(IntegrationTestFixture fixture)
    {
        _fixture = fixture;
    }

    [Fact]
    public async Task CleanupExpiredAsync_MarksTransfersExpired()
    {
        var payload = "expired"u8.ToArray();
        var transferId = Guid.NewGuid();
        var tempFilePath = Path.Combine(_fixture.TempPath, $"{transferId:D}.bin");
        await File.WriteAllBytesAsync(tempFilePath, payload);

        using var scope = _fixture.Services.CreateScope();
        var repository = scope.ServiceProvider.GetRequiredService<IFileTransferRepository>();
        var kafkaService = scope.ServiceProvider.GetRequiredService<FileTransferKafkaService>();
        var cleanupJob = scope.ServiceProvider.GetRequiredService<FileTransferCleanupJob>();

        var transfer = new FileTransferDbModel
        {
            Id = transferId,
            FileName = "expired.txt",
            ContentType = "text/plain",
            SizeBytes = payload.Length,
            SenderUserId = Guid.NewGuid(),
            TargetUserId = Guid.NewGuid(),
            SenderDeviceId = "sender",
            TargetDeviceId = "target",
            Status = FileTransferStatus.Available,
            ChunkSizeBytes = 1024,
            PacketCount = 0,
            KafkaTopic = "binarystars.transfers.tests",
            KafkaAuthMode = "Scram",
            CreatedAt = DateTimeOffset.UtcNow.AddMinutes(-120),
            ExpiresAt = DateTimeOffset.UtcNow.AddMinutes(-60)
        };

        await repository.AddAsync(transfer, CancellationToken.None);
        await repository.SaveChangesAsync(CancellationToken.None);

        var publish = await kafkaService.PublishFromFileAsync(
            transfer,
            tempFilePath,
            KafkaAuthMode.Scram,
            null,
            transfer.ChunkSizeBytes,
            CancellationToken.None);

        transfer.PacketCount = publish.PacketCount;
        transfer.KafkaPartition = publish.Partition;
        transfer.KafkaStartOffset = publish.StartOffset;
        transfer.KafkaEndOffset = publish.EndOffset;
        await repository.UpdateAsync(transfer, CancellationToken.None);
        await repository.SaveChangesAsync(CancellationToken.None);

        await cleanupJob.CleanupExpiredAsync();

        var updated = await repository.GetByIdAsync(transferId, CancellationToken.None);
        Assert.NotNull(updated);
        Assert.Equal(FileTransferStatus.Expired, updated!.Status);

        if (File.Exists(tempFilePath))
        {
            File.Delete(tempFilePath);
        }
    }
}
