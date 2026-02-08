using BinaryStars.Application.Databases.DatabaseModels.Notes;

namespace BinaryStars.Application.Databases.Repositories.Notes;

/// <summary>
/// Repository abstraction for note persistence.
/// </summary>
public interface INotesRepository
{
    /// <summary>
    /// Gets a note by identifier.
    /// </summary>
    /// <param name="id">The note identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The note, or null if not found.</returns>
    Task<NoteDbModel?> GetByIdAsync(Guid id, CancellationToken cancellationToken);

    /// <summary>
    /// Gets all notes for a user.
    /// </summary>
    /// <param name="userId">The user identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The list of notes.</returns>
    Task<List<NoteDbModel>> GetByUserIdAsync(Guid userId, CancellationToken cancellationToken);

    /// <summary>
    /// Gets notes for a user filtered by device.
    /// </summary>
    /// <param name="userId">The user identifier.</param>
    /// <param name="deviceId">The device identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The list of notes.</returns>
    Task<List<NoteDbModel>> GetByUserAndDeviceIdAsync(Guid userId, string deviceId, CancellationToken cancellationToken);

    /// <summary>
    /// Gets notes signed by a specific device.
    /// </summary>
    /// <param name="deviceId">The device identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The list of notes.</returns>
    Task<List<NoteDbModel>> GetByDeviceIdAsync(string deviceId, CancellationToken cancellationToken);

    /// <summary>
    /// Adds a new note record.
    /// </summary>
    /// <param name="note">The note to add.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    Task AddAsync(NoteDbModel note, CancellationToken cancellationToken);

    /// <summary>
    /// Updates an existing note record.
    /// </summary>
    /// <param name="note">The note to update.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    Task UpdateAsync(NoteDbModel note, CancellationToken cancellationToken);

    /// <summary>
    /// Deletes a note record.
    /// </summary>
    /// <param name="note">The note to delete.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    Task DeleteAsync(NoteDbModel note, CancellationToken cancellationToken);

    /// <summary>
    /// Deletes all notes signed by a specific device.
    /// </summary>
    /// <param name="deviceId">The device identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    Task DeleteByDeviceIdAsync(string deviceId, CancellationToken cancellationToken);

    /// <summary>
    /// Persists pending changes.
    /// </summary>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    Task SaveChangesAsync(CancellationToken cancellationToken);
}
