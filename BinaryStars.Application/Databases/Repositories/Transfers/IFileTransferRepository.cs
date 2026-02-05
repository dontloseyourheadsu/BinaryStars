using BinaryStars.Application.Databases.DatabaseModels.Transfers;
using BinaryStars.Domain.Transfers;

namespace BinaryStars.Application.Databases.Repositories.Transfers;

public interface IFileTransferRepository
{
    Task<FileTransferDbModel?> GetByIdAsync(Guid id, CancellationToken cancellationToken);
    Task<List<FileTransferDbModel>> GetByUserAsync(Guid userId, CancellationToken cancellationToken);
    Task<List<FileTransferDbModel>> GetByTargetDeviceIdAsync(string deviceId, CancellationToken cancellationToken);
    Task<List<FileTransferDbModel>> GetPendingByTargetDeviceIdAsync(string deviceId, CancellationToken cancellationToken);
    Task<bool> HasPendingForDeviceAsync(string deviceId, CancellationToken cancellationToken);
    Task AddAsync(FileTransferDbModel transfer, CancellationToken cancellationToken);
    Task UpdateAsync(FileTransferDbModel transfer, CancellationToken cancellationToken);
    Task SaveChangesAsync(CancellationToken cancellationToken);
    Task<List<FileTransferDbModel>> GetExpiredAsync(DateTimeOffset now, CancellationToken cancellationToken);
}
