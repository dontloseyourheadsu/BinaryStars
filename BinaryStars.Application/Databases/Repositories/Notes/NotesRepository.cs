using BinaryStars.Application.Databases.DatabaseContexts;
using BinaryStars.Application.Databases.DatabaseModels.Notes;
using Microsoft.EntityFrameworkCore;

namespace BinaryStars.Application.Databases.Repositories.Notes;

/// <summary>
/// Entity Framework repository for note persistence.
/// </summary>
public class NotesRepository : INotesRepository
{
    private readonly ApplicationDbContext _context;

    /// <summary>
    /// Initializes a new instance of the <see cref="NotesRepository"/> class.
    /// </summary>
    /// <param name="context">The application database context.</param>
    public NotesRepository(ApplicationDbContext context)
    {
        _context = context;
    }

    /// <inheritdoc />
    public Task<NoteDbModel?> GetByIdAsync(Guid id, CancellationToken cancellationToken)
    {
        return _context.Notes
            .FirstOrDefaultAsync(n => n.Id == id, cancellationToken);
    }

    /// <inheritdoc />
    public Task<List<NoteDbModel>> GetByUserIdAsync(Guid userId, CancellationToken cancellationToken)
    {
        return _context.Notes
            .Where(n => n.UserId == userId)
            .OrderByDescending(n => n.CreatedAt)
            .ToListAsync(cancellationToken);
    }

    /// <inheritdoc />
    public Task<List<NoteDbModel>> GetByUserAndDeviceIdAsync(Guid userId, string deviceId, CancellationToken cancellationToken)
    {
        return _context.Notes
            .Where(n => n.UserId == userId && n.SignedByDeviceId == deviceId)
            .OrderByDescending(n => n.CreatedAt)
            .ToListAsync(cancellationToken);
    }

    /// <inheritdoc />
    public Task<List<NoteDbModel>> GetByDeviceIdAsync(string deviceId, CancellationToken cancellationToken)
    {
        return _context.Notes
            .Where(n => n.SignedByDeviceId == deviceId)
            .ToListAsync(cancellationToken);
    }

    /// <inheritdoc />
    public async Task AddAsync(NoteDbModel note, CancellationToken cancellationToken)
    {
        await _context.Notes.AddAsync(note, cancellationToken);
    }

    /// <inheritdoc />
    public Task UpdateAsync(NoteDbModel note, CancellationToken cancellationToken)
    {
        _context.Notes.Update(note);
        return Task.CompletedTask;
    }

    /// <inheritdoc />
    public Task DeleteAsync(NoteDbModel note, CancellationToken cancellationToken)
    {
        _context.Notes.Remove(note);
        return Task.CompletedTask;
    }

    /// <inheritdoc />
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
