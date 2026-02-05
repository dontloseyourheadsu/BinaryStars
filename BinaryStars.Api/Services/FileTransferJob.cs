using BinaryStars.Application.Databases.Repositories.Transfers;
using BinaryStars.Application.Services.Transfers;
using BinaryStars.Domain.Transfers;
using Microsoft.Extensions.Options;
using BinaryStars.Api.Models;

namespace BinaryStars.Api.Services;

public record FileTransferJobRequest(Guid TransferId, string TempFilePath, string KafkaAuthMode, string? OAuthToken);

public class FileTransferJob
{
    private readonly IFileTransferRepository _repository;
    private readonly IFileTransfersWriteService _writeService;
    private readonly FileTransferKafkaService _kafkaService;
    private readonly FileTransferSettings _settings;
    private readonly ILogger<FileTransferJob> _logger;

    public FileTransferJob(
        IFileTransferRepository repository,
        IFileTransfersWriteService writeService,
        FileTransferKafkaService kafkaService,
        IOptions<FileTransferSettings> settings,
        ILogger<FileTransferJob> logger)
    {
        _repository = repository;
        _writeService = writeService;
        _kafkaService = kafkaService;
        _settings = settings.Value;
        _logger = logger;
    }

    public async Task SendToKafkaAsync(FileTransferJobRequest request)
    {
        var transfer = await _repository.GetByIdAsync(request.TransferId, CancellationToken.None);
        if (transfer == null)
        {
            _logger.LogWarning("Transfer {TransferId} not found for Kafka publish.", request.TransferId);
            return;
        }

        if (!File.Exists(request.TempFilePath))
        {
            _logger.LogWarning("Temp file {TempFilePath} missing for transfer {TransferId}.", request.TempFilePath, request.TransferId);
            await _writeService.UpdateStatusAsync(request.TransferId, FileTransferStatus.Failed, "Upload source missing", null, CancellationToken.None);
            return;
        }

        var authMode = ParseAuthMode(request.KafkaAuthMode);
        try
        {
            await _writeService.UpdateStatusAsync(request.TransferId, FileTransferStatus.Uploading, null, null, CancellationToken.None);

            var publishResult = await _kafkaService.PublishFromFileAsync(
                transfer,
                request.TempFilePath,
                authMode,
                request.OAuthToken,
                _settings.ChunkSizeBytes,
                CancellationToken.None);

            await _writeService.UpdateKafkaInfoAsync(
                request.TransferId,
                publishResult.PacketCount,
                _settings.ChunkSizeBytes,
                publishResult.Partition,
                publishResult.StartOffset,
                publishResult.EndOffset,
                CancellationToken.None);

            await _writeService.UpdateStatusAsync(request.TransferId, FileTransferStatus.Available, null, null, CancellationToken.None);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to publish transfer {TransferId} to Kafka.", request.TransferId);
            await _writeService.UpdateStatusAsync(request.TransferId, FileTransferStatus.Failed, ex.Message, null, CancellationToken.None);
        }
        finally
        {
            try
            {
                if (File.Exists(request.TempFilePath))
                {
                    File.Delete(request.TempFilePath);
                }
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Failed to delete temp file {TempFilePath}.", request.TempFilePath);
            }
        }
    }

    private static KafkaAuthMode ParseAuthMode(string mode)
    {
        return Enum.TryParse<KafkaAuthMode>(mode, true, out var parsed) ? parsed : KafkaAuthMode.Scram;
    }
}
