using BinaryStars.Application.Databases.Repositories.Devices;
using BinaryStars.Application.Databases.Repositories.Notes;
using BinaryStars.Application.Mappers.Notes;
using BinaryStars.Domain;
using BinaryStars.Domain.Notes;
using BinaryStars.Domain.Errors.Notes;

namespace BinaryStars.Application.Services.Notes;

public interface INotesWriteService
{
    Task<Result<NoteResponse>> CreateNoteAsync(Guid userId, CreateNoteRequest request, CancellationToken cancellationToken);
    Task<Result<NoteResponse>> UpdateNoteAsync(Guid userId, UpdateNoteRequest request, CancellationToken cancellationToken);
    Task<Result> DeleteNoteAsync(Guid noteId, Guid userId, CancellationToken cancellationToken);
}

public class NotesWriteService : INotesWriteService
{
    private readonly INotesRepository _notesRepository;
    private readonly IDeviceRepository _deviceRepository;

    public NotesWriteService(INotesRepository notesRepository, IDeviceRepository deviceRepository)
    {
        _notesRepository = notesRepository;
        _deviceRepository = deviceRepository;
    }

    public async Task<Result<NoteResponse>> CreateNoteAsync(Guid userId, CreateNoteRequest request, CancellationToken cancellationToken)
    {
        // Validate device is linked to user
        var device = await _deviceRepository.GetByIdAsync(request.DeviceId, cancellationToken);
        if (device == null || device.UserId != userId)
            return Result<NoteResponse>.Failure(NoteErrors.DeviceNotLinkedToUser);

        var note = new Note(
            Guid.NewGuid(),
            request.Name,
            userId,
            request.DeviceId,
            request.ContentType,
            DateTimeOffset.UtcNow,
            DateTimeOffset.UtcNow
        );

        var dbModel = note.ToDb();
        dbModel.Content = request.Content;

        await _notesRepository.AddAsync(dbModel, cancellationToken);
        await _notesRepository.SaveChangesAsync(cancellationToken);

        return Result<NoteResponse>.Success(new NoteResponse(
            dbModel.Id,
            dbModel.Name,
            dbModel.SignedByDeviceId,
            dbModel.ContentType,
            dbModel.Content,
            dbModel.CreatedAt,
            dbModel.UpdatedAt
        ));
    }

    public async Task<Result<NoteResponse>> UpdateNoteAsync(Guid userId, UpdateNoteRequest request, CancellationToken cancellationToken)
    {
        var note = await _notesRepository.GetByIdAsync(request.NoteId, cancellationToken);

        if (note == null)
            return Result<NoteResponse>.Failure(NoteErrors.NoteNotFound);

        if (note.UserId != userId)
            return Result<NoteResponse>.Failure(NoteErrors.NoteNotOwnedByUser);

        note.Name = request.Name;
        note.Content = request.Content;
        note.UpdatedAt = DateTimeOffset.UtcNow;

        await _notesRepository.UpdateAsync(note, cancellationToken);
        await _notesRepository.SaveChangesAsync(cancellationToken);

        return Result<NoteResponse>.Success(new NoteResponse(
            note.Id,
            note.Name,
            note.SignedByDeviceId,
            note.ContentType,
            note.Content,
            note.CreatedAt,
            note.UpdatedAt
        ));
    }

    public async Task<Result> DeleteNoteAsync(Guid noteId, Guid userId, CancellationToken cancellationToken)
    {
        var note = await _notesRepository.GetByIdAsync(noteId, cancellationToken);

        if (note == null)
            return Result.Failure(NoteErrors.NoteNotFound);

        if (note.UserId != userId)
            return Result.Failure(NoteErrors.NoteNotOwnedByUser);

        await _notesRepository.DeleteAsync(note, cancellationToken);
        await _notesRepository.SaveChangesAsync(cancellationToken);

        return Result.Success();
    }
}
