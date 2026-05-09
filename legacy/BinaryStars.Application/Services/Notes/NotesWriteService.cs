using BinaryStars.Application.Databases.Repositories.Devices;
using BinaryStars.Application.Databases.Repositories.Notes;
using BinaryStars.Application.Mappers.Notes;
using BinaryStars.Domain;
using BinaryStars.Domain.Notes;
using BinaryStars.Domain.Errors.Notes;
using System.Text.Json;

namespace BinaryStars.Application.Services.Notes;

/// <summary>
/// Write-only note operations exposed by the application layer.
/// </summary>
public interface INotesWriteService
{
    /// <summary>
    /// Creates a new note for the specified user.
    /// </summary>
    /// <param name="userId">The user identifier.</param>
    /// <param name="request">The note creation request.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The created note response or a failure result.</returns>
    Task<Result<NoteResponse>> CreateNoteAsync(Guid userId, CreateNoteRequest request, CancellationToken cancellationToken);

    /// <summary>
    /// Updates an existing note for the specified user.
    /// </summary>
    /// <param name="userId">The user identifier.</param>
    /// <param name="request">The note update request.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The updated note response or a failure result.</returns>
    Task<Result<NoteResponse>> UpdateNoteAsync(Guid userId, UpdateNoteRequest request, CancellationToken cancellationToken);

    /// <summary>
    /// Deletes a note owned by the specified user.
    /// </summary>
    /// <param name="noteId">The note identifier.</param>
    /// <param name="userId">The user identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>A success or failure result.</returns>
    Task<Result> DeleteNoteAsync(Guid noteId, Guid userId, CancellationToken cancellationToken);
}

/// <summary>
/// Application service for creating, updating, and deleting notes.
/// </summary>
public class NotesWriteService : INotesWriteService
{
    private readonly INotesRepository _notesRepository;
    private readonly IDeviceRepository _deviceRepository;

    /// <summary>
    /// Initializes a new instance of the <see cref="NotesWriteService"/> class.
    /// </summary>
    /// <param name="notesRepository">Repository for note data.</param>
    /// <param name="deviceRepository">Repository for device data.</param>
    public NotesWriteService(INotesRepository notesRepository, IDeviceRepository deviceRepository)
    {
        _notesRepository = notesRepository;
        _deviceRepository = deviceRepository;
    }

    /// <inheritdoc />
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
        dbModel.Content = NormalizeContentForStorage(request.Content);

        await _notesRepository.AddAsync(dbModel, cancellationToken);
        await _notesRepository.SaveChangesAsync(cancellationToken);

        return Result<NoteResponse>.Success(new NoteResponse(
            dbModel.Id,
            dbModel.Name,
            dbModel.SignedByDeviceId,
            device.Name,
            dbModel.ContentType,
            ExtractContentForResponse(dbModel.Content),
            dbModel.CreatedAt,
            dbModel.UpdatedAt
        ));
    }

    /// <inheritdoc />
    public async Task<Result<NoteResponse>> UpdateNoteAsync(Guid userId, UpdateNoteRequest request, CancellationToken cancellationToken)
    {
        var note = await _notesRepository.GetByIdAsync(request.NoteId, cancellationToken);

        if (note == null)
            return Result<NoteResponse>.Failure(NoteErrors.NoteNotFound);

        if (note.UserId != userId)
            return Result<NoteResponse>.Failure(NoteErrors.NoteNotOwnedByUser);

        note.Name = request.Name;
        note.Content = NormalizeContentForStorage(request.Content);
        note.UpdatedAt = DateTimeOffset.UtcNow;

        await _notesRepository.UpdateAsync(note, cancellationToken);
        await _notesRepository.SaveChangesAsync(cancellationToken);

        var device = await _deviceRepository.GetByIdAsync(note.SignedByDeviceId, cancellationToken);
        var deviceName = device?.Name ?? "Unknown Device";
        return Result<NoteResponse>.Success(new NoteResponse(
            note.Id,
            note.Name,
            note.SignedByDeviceId,
            deviceName,
            note.ContentType,
            ExtractContentForResponse(note.Content),
            note.CreatedAt,
            note.UpdatedAt
        ));
    }

    /// <inheritdoc />
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

    /// <summary>
    /// Normalizes raw note content into a JSON payload for storage.
    /// </summary>
    /// <param name="content">The raw note content.</param>
    /// <returns>The JSON payload to store.</returns>
    private static string NormalizeContentForStorage(string content)
    {
        return JsonSerializer.Serialize(new { text = content });
    }

    /// <summary>
    /// Extracts the user-facing text content from the stored JSON payload.
    /// </summary>
    /// <param name="storedContent">The stored JSON note content.</param>
    /// <returns>The extracted text content.</returns>
    private static string ExtractContentForResponse(string storedContent)
    {
        try
        {
            using var doc = JsonDocument.Parse(storedContent);
            if (doc.RootElement.ValueKind == JsonValueKind.Object && doc.RootElement.TryGetProperty("text", out var textElement))
            {
                return textElement.GetString() ?? string.Empty;
            }

            if (doc.RootElement.ValueKind == JsonValueKind.String)
            {
                return doc.RootElement.GetString() ?? string.Empty;
            }
        }
        catch (JsonException)
        {
            // Ignore parse errors and return raw content
        }

        return storedContent;
    }
}
