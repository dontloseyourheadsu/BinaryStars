using BinaryStars.Application.Databases.DatabaseContexts;
using BinaryStars.Application.Databases.DatabaseModels.Notes;
using Microsoft.EntityFrameworkCore;

namespace BinaryStars.Application.Databases.Repositories.Notes;

public class NotesRepository : INotesRepository
{
    private readonly ApplicationDbContext _context;

    public NotesRepository(ApplicationDbContext context)
    {
        _context = context;
    }

    public Task<NoteDbModel?> GetByIdAsync(Guid id, CancellationToken cancellationToken)
    {
        return _context.Notes
            .FirstOrDefaultAsync(n => n.Id == id, cancellationToken);
    }

    public Task<List<NoteDbModel>> GetByUserIdAsync(Guid userId, CancellationToken cancellationToken)
    {
        return _context.Notes
            .Where(n => n.UserId == userId)
            .OrderByDescending(n => n.CreatedAt)
            .ToListAsync(cancellationToken);
    }

    public Task<List<NoteDbModel>> GetByUserAndDeviceIdAsync(Guid userId, string deviceId, CancellationToken cancellationToken)
    {
        return _context.Notes
            .Where(n => n.UserId == userId && n.SignedByDeviceId == deviceId)
            .OrderByDescending(n => n.CreatedAt)
            .ToListAsync(cancellationToken);
    }

    public Task<List<NoteDbModel>> GetByDeviceIdAsync(string deviceId, CancellationToken cancellationToken)
    {
        return _context.Notes
            .Where(n => n.SignedByDeviceId == deviceId)
            .ToListAsync(cancellationToken);
    }

    public async Task AddAsync(NoteDbModel note, CancellationToken cancellationToken)
    {
        await _context.Notes.AddAsync(note, cancellationToken);
    }

    public Task UpdateAsync(NoteDbModel note, CancellationToken cancellationToken)
    {
        _context.Notes.Update(note);
        return Task.CompletedTask;
    }

    public Task DeleteAsync(NoteDbModel note, CancellationToken cancellationToken)
    {
        _context.Notes.Remove(note);
        return Task.CompletedTask;
    }

    public async Task DeleteByDeviceIdAsync(string deviceId, CancellationToken cancellationToken)
    {
        var notes = await GetByDeviceIdAsync(deviceId, cancellationToken);
        foreach (var note in notes)
        {
            _context.Notes.Remove(note);
        }
    }

    public Task SaveChangesAsync(CancellationToken cancellationToken)
    {
        return _context.SaveChangesAsync(cancellationToken);
    }
}
