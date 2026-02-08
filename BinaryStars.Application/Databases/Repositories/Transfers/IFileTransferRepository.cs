using BinaryStars.Application.Databases.DatabaseModels.Transfers;
using BinaryStars.Domain.Transfers;

namespace BinaryStars.Application.Databases.Repositories.Transfers;

/// <summary>
/// Repository abstraction for file transfer persistence.
/// </summary>
public interface IFileTransferRepository
{
    /// <summary>
    /// Gets a transfer by identifier.
    /// </summary>
    /// <param name="id">The transfer identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The transfer, or null if not found.</returns>
    Task<FileTransferDbModel?> GetByIdAsync(Guid id, CancellationToken cancellationToken);

    /// <summary>
    /// Gets all transfers for a user (sent or received).
    /// </summary>
    /// <param name="userId">The user identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The list of transfers.</returns>
    Task<List<FileTransferDbModel>> GetByUserAsync(Guid userId, CancellationToken cancellationToken);

    /// <summary>
    /// Gets transfers targeting a specific device.
    /// </summary>
    /// <param name="deviceId">The device identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The list of transfers.</returns>
    Task<List<FileTransferDbModel>> GetByTargetDeviceIdAsync(string deviceId, CancellationToken cancellationToken);

    /// <summary>
    /// Gets transfers available for download by a device.
    /// </summary>
    /// <param name="deviceId">The device identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The list of pending transfers.</returns>
    Task<List<FileTransferDbModel>> GetPendingByTargetDeviceIdAsync(string deviceId, CancellationToken cancellationToken);

    /// <summary>
    /// Determines whether a device has any queued, uploading, or available transfers.
    /// </summary>
    /// <param name="deviceId">The device identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>True if pending transfers exist; otherwise false.</returns>
    Task<bool> HasPendingForDeviceAsync(string deviceId, CancellationToken cancellationToken);

    /// <summary>
    /// Adds a new transfer record.
    /// </summary>
    /// <param name="transfer">The transfer to add.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    Task AddAsync(FileTransferDbModel transfer, CancellationToken cancellationToken);

    /// <summary>
    /// Updates an existing transfer record.
    /// </summary>
    /// <param name="transfer">The transfer to update.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    Task UpdateAsync(FileTransferDbModel transfer, CancellationToken cancellationToken);

    /// <summary>
    /// Persists pending changes.
    /// </summary>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    Task SaveChangesAsync(CancellationToken cancellationToken);

    /// <summary>
    /// Gets transfers that have expired and are not already finalized.
    /// </summary>
    /// <param name="now">The timestamp used to evaluate expiration.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The list of expired transfers.</returns>
    Task<List<FileTransferDbModel>> GetExpiredAsync(DateTimeOffset now, CancellationToken cancellationToken);
}
