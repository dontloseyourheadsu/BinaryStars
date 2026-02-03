using BinaryStars.Application.Databases.Repositories.Notes;
using BinaryStars.Application.Mappers.Notes;
using BinaryStars.Domain;
using BinaryStars.Domain.Notes;
using BinaryStars.Domain.Errors.Notes;

namespace BinaryStars.Application.Services.Notes;

public record CreateNoteRequest(
    string Name,
    string DeviceId,
    NoteType ContentType,
    string Content);

public record UpdateNoteRequest(
    Guid NoteId,
    string Name,
    string Content);

public record NoteResponse(
    Guid Id,
    string Name,
    string SignedByDeviceId,
    NoteType ContentType,
    string Content,
    DateTimeOffset CreatedAt,
    DateTimeOffset UpdatedAt);

public interface INotesReadService
{
    Task<Result<List<NoteResponse>>> GetNotesByUserAsync(Guid userId, CancellationToken cancellationToken);
    Task<Result<List<NoteResponse>>> GetNotesByUserAndDeviceAsync(Guid userId, string deviceId, CancellationToken cancellationToken);
    Task<Result<NoteResponse>> GetNoteByIdAsync(Guid noteId, Guid userId, CancellationToken cancellationToken);
}

public class NotesReadService : INotesReadService
{
    private readonly INotesRepository _repository;

    public NotesReadService(INotesRepository repository)
    {
        _repository = repository;
    }

    public async Task<Result<List<NoteResponse>>> GetNotesByUserAsync(Guid userId, CancellationToken cancellationToken)
    {
        var notes = await _repository.GetByUserIdAsync(userId, cancellationToken);
        var responses = notes.Select(MapToResponse).ToList();

        return Result<List<NoteResponse>>.Success(responses);
    }

    public async Task<Result<List<NoteResponse>>> GetNotesByUserAndDeviceAsync(Guid userId, string deviceId, CancellationToken cancellationToken)
    {
        var notes = await _repository.GetByUserAndDeviceIdAsync(userId, deviceId, cancellationToken);
        var responses = notes.Select(MapToResponse).ToList();

        return Result<List<NoteResponse>>.Success(responses);
    }

    public async Task<Result<NoteResponse>> GetNoteByIdAsync(Guid noteId, Guid userId, CancellationToken cancellationToken)
    {
        var note = await _repository.GetByIdAsync(noteId, cancellationToken);

        if (note == null)
            return Result<NoteResponse>.Failure(NoteErrors.NoteNotFound);

        if (note.UserId != userId)
            return Result<NoteResponse>.Failure(NoteErrors.NoteNotOwnedByUser);

        return Result<NoteResponse>.Success(MapToResponse(note));
    }

    private static NoteResponse MapToResponse(dynamic note)
    {
        return new NoteResponse(
            note.Id,
            note.Name,
            note.SignedByDeviceId,
            note.ContentType,
            note.Content,
            note.CreatedAt,
            note.UpdatedAt
        );
    }
}
