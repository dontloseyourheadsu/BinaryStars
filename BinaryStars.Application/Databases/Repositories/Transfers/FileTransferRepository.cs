using BinaryStars.Application.Databases.DatabaseContexts;
using BinaryStars.Application.Databases.DatabaseModels.Transfers;
using BinaryStars.Domain.Transfers;
using Microsoft.EntityFrameworkCore;

namespace BinaryStars.Application.Databases.Repositories.Transfers;

/// <summary>
/// Entity Framework repository for file transfer persistence.
/// </summary>
public class FileTransferRepository : IFileTransferRepository
{
    private readonly ApplicationDbContext _context;

    /// <summary>
    /// Initializes a new instance of the <see cref="FileTransferRepository"/> class.
    /// </summary>
    /// <param name="context">The application database context.</param>
    public FileTransferRepository(ApplicationDbContext context)
    {
        _context = context;
    }

    /// <inheritdoc />
    public Task<FileTransferDbModel?> GetByIdAsync(Guid id, CancellationToken cancellationToken)
    {
        return _context.FileTransfers
            .FirstOrDefaultAsync(t => t.Id == id, cancellationToken);
    }

    /// <inheritdoc />
    public Task<List<FileTransferDbModel>> GetByUserAsync(Guid userId, CancellationToken cancellationToken)
    {
        return _context.FileTransfers
            .Where(t => t.SenderUserId == userId || t.TargetUserId == userId)
            .OrderByDescending(t => t.CreatedAt)
            .ToListAsync(cancellationToken);
    }

    /// <inheritdoc />
    public Task<List<FileTransferDbModel>> GetByTargetDeviceIdAsync(string deviceId, CancellationToken cancellationToken)
    {
        return _context.FileTransfers
            .Where(t => t.TargetDeviceId == deviceId)
            .OrderByDescending(t => t.CreatedAt)
            .ToListAsync(cancellationToken);
    }

    /// <inheritdoc />
    public Task<List<FileTransferDbModel>> GetPendingByTargetDeviceIdAsync(string deviceId, CancellationToken cancellationToken)
    {
        return _context.FileTransfers
            .Where(t => t.TargetDeviceId == deviceId && t.Status == FileTransferStatus.Available)
            .OrderByDescending(t => t.CreatedAt)
            .ToListAsync(cancellationToken);
    }

    /// <inheritdoc />
    public Task<bool> HasPendingForDeviceAsync(string deviceId, CancellationToken cancellationToken)
    {
        return _context.FileTransfers
            .AnyAsync(t => t.TargetDeviceId == deviceId &&
                (t.Status == FileTransferStatus.Queued || t.Status == FileTransferStatus.Uploading || t.Status == FileTransferStatus.Available),
                cancellationToken);
    }

    /// <inheritdoc />
    public Task AddAsync(FileTransferDbModel transfer, CancellationToken cancellationToken)
    {
        return _context.FileTransfers.AddAsync(transfer, cancellationToken).AsTask();
    }

    /// <inheritdoc />
    public Task UpdateAsync(FileTransferDbModel transfer, CancellationToken cancellationToken)
    {
        _context.FileTransfers.Update(transfer);
        return Task.CompletedTask;
    }

    /// <inheritdoc />
    public Task SaveChangesAsync(CancellationToken cancellationToken)
    {
        return _context.SaveChangesAsync(cancellationToken);
    }

    /// <inheritdoc />
    public Task<List<FileTransferDbModel>> GetExpiredAsync(DateTimeOffset now, CancellationToken cancellationToken)
    {
        return _context.FileTransfers
            .Where(t => t.ExpiresAt <= now && t.Status != FileTransferStatus.Expired && t.Status != FileTransferStatus.Downloaded)
            .ToListAsync(cancellationToken);
    }
}
