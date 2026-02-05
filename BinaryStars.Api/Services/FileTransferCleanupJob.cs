using BinaryStars.Application.Databases.Repositories.Transfers;
using BinaryStars.Application.Services.Transfers;
using BinaryStars.Domain.Transfers;

namespace BinaryStars.Api.Services;

public class FileTransferCleanupJob
{
    private readonly IFileTransferRepository _repository;
    private readonly IFileTransfersWriteService _writeService;
    private readonly FileTransferKafkaService _kafkaService;
    private readonly ILogger<FileTransferCleanupJob> _logger;

    public FileTransferCleanupJob(
        IFileTransferRepository repository,
        IFileTransfersWriteService writeService,
        FileTransferKafkaService kafkaService,
        ILogger<FileTransferCleanupJob> logger)
    {
        _repository = repository;
        _writeService = writeService;
        _kafkaService = kafkaService;
        _logger = logger;
    }

    public async Task CleanupExpiredAsync()
    {
        var expired = await _repository.GetExpiredAsync(DateTimeOffset.UtcNow, CancellationToken.None);
        foreach (var transfer in expired)
        {
            try
            {
                await _kafkaService.DeleteTransferPacketsAsync(transfer, KafkaAuthMode.Scram, null, CancellationToken.None);
                await _writeService.UpdateStatusAsync(transfer.Id, FileTransferStatus.Expired, "Expired", null, CancellationToken.None);
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Failed to cleanup expired transfer {TransferId}.", transfer.Id);
            }
        }
    }


}
