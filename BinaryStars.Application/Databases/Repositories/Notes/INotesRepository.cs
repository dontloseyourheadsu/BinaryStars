using BinaryStars.Application.Databases.DatabaseModels.Notes;

namespace BinaryStars.Application.Databases.Repositories.Notes;

public interface INotesRepository
{
    Task<NoteDbModel?> GetByIdAsync(Guid id, CancellationToken cancellationToken);
    Task<List<NoteDbModel>> GetByUserIdAsync(Guid userId, CancellationToken cancellationToken);
    Task<List<NoteDbModel>> GetByUserAndDeviceIdAsync(Guid userId, string deviceId, CancellationToken cancellationToken);
    Task<List<NoteDbModel>> GetByDeviceIdAsync(string deviceId, CancellationToken cancellationToken);
    Task AddAsync(NoteDbModel note, CancellationToken cancellationToken);
    Task UpdateAsync(NoteDbModel note, CancellationToken cancellationToken);
    Task DeleteAsync(NoteDbModel note, CancellationToken cancellationToken);
    Task DeleteByDeviceIdAsync(string deviceId, CancellationToken cancellationToken);
    Task SaveChangesAsync(CancellationToken cancellationToken);
}
