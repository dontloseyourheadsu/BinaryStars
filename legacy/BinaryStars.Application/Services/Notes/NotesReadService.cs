using BinaryStars.Application.Databases.Repositories.Notes;
using BinaryStars.Application.Mappers.Notes;
using BinaryStars.Application.Databases.Repositories.Devices;
using BinaryStars.Domain;
using BinaryStars.Domain.Notes;
using BinaryStars.Domain.Errors.Notes;
using System.Text.Json;

namespace BinaryStars.Application.Services.Notes;

/// <summary>
/// Request payload for creating a new note.
/// </summary>
/// <param name="Name">The note title.</param>
/// <param name="DeviceId">The device identifier that signs the note.</param>
/// <param name="ContentType">The note content type.</param>
/// <param name="Content">The note body content.</param>
public record CreateNoteRequest(
    string Name,
    string DeviceId,
    NoteType ContentType,
    string Content);

/// <summary>
/// Request payload for updating an existing note.
/// </summary>
/// <param name="NoteId">The note identifier.</param>
/// <param name="Name">The updated note title.</param>
/// <param name="Content">The updated note content.</param>
public record UpdateNoteRequest(
    Guid NoteId,
    string Name,
    string Content);

/// <summary>
/// Read model returned by note APIs.
/// </summary>
/// <param name="Id">The note identifier.</param>
/// <param name="Name">The note title.</param>
/// <param name="SignedByDeviceId">The device identifier that signed the note.</param>
/// <param name="SignedByDeviceName">The device name that signed the note.</param>
/// <param name="ContentType">The note content type.</param>
/// <param name="Content">The note content returned to clients.</param>
/// <param name="CreatedAt">The creation timestamp.</param>
/// <param name="UpdatedAt">The last updated timestamp.</param>
public record NoteResponse(
    Guid Id,
    string Name,
    string SignedByDeviceId,
    string SignedByDeviceName,
    NoteType ContentType,
    string Content,
    DateTimeOffset CreatedAt,
    DateTimeOffset UpdatedAt);

/// <summary>
/// Read-only note operations exposed by the application layer.
/// </summary>
public interface INotesReadService
{
    /// <summary>
    /// Gets all notes owned by the specified user.
    /// </summary>
    /// <param name="userId">The user identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>A list of notes or a failure result.</returns>
    Task<Result<List<NoteResponse>>> GetNotesByUserAsync(Guid userId, CancellationToken cancellationToken);

    /// <summary>
    /// Gets all notes owned by the user for a specific device.
    /// </summary>
    /// <param name="userId">The user identifier.</param>
    /// <param name="deviceId">The device identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>A list of notes or a failure result.</returns>
    Task<Result<List<NoteResponse>>> GetNotesByUserAndDeviceAsync(Guid userId, string deviceId, CancellationToken cancellationToken);

    /// <summary>
    /// Gets a specific note owned by the user.
    /// </summary>
    /// <param name="noteId">The note identifier.</param>
    /// <param name="userId">The user identifier.</param>
    /// <param name="cancellationToken">A token to cancel the operation.</param>
    /// <returns>The note or a failure result.</returns>
    Task<Result<NoteResponse>> GetNoteByIdAsync(Guid noteId, Guid userId, CancellationToken cancellationToken);
}

/// <summary>
/// Application service for reading note data.
/// </summary>
public class NotesReadService : INotesReadService
{
    private readonly INotesRepository _repository;
    private readonly IDeviceRepository _deviceRepository;

    /// <summary>
    /// Initializes a new instance of the <see cref="NotesReadService"/> class.
    /// </summary>
    /// <param name="repository">Repository for note data.</param>
    /// <param name="deviceRepository">Repository for device data.</param>
    public NotesReadService(INotesRepository repository, IDeviceRepository deviceRepository)
    {
        _repository = repository;
        _deviceRepository = deviceRepository;
    }

    /// <inheritdoc />
    public async Task<Result<List<NoteResponse>>> GetNotesByUserAsync(Guid userId, CancellationToken cancellationToken)
    {
        var notes = await _repository.GetByUserIdAsync(userId, cancellationToken);
        var devices = await _deviceRepository.GetDevicesByUserIdAsync(userId, cancellationToken);
        var deviceLookup = devices.ToDictionary(d => d.Id, d => d.Name);
        var responses = notes.Select(note => MapToResponse(note, deviceLookup.TryGetValue(note.SignedByDeviceId, out var name) ? name : "Unknown Device")).ToList();

        return Result<List<NoteResponse>>.Success(responses);
    }

    /// <inheritdoc />
    public async Task<Result<List<NoteResponse>>> GetNotesByUserAndDeviceAsync(Guid userId, string deviceId, CancellationToken cancellationToken)
    {
        var notes = await _repository.GetByUserAndDeviceIdAsync(userId, deviceId, cancellationToken);
        var device = await _deviceRepository.GetByIdAsync(deviceId, cancellationToken);
        var deviceName = device?.Name ?? "Unknown Device";
        var responses = notes.Select(note => MapToResponse(note, deviceName)).ToList();

        return Result<List<NoteResponse>>.Success(responses);
    }

    /// <inheritdoc />
    public async Task<Result<NoteResponse>> GetNoteByIdAsync(Guid noteId, Guid userId, CancellationToken cancellationToken)
    {
        var note = await _repository.GetByIdAsync(noteId, cancellationToken);

        if (note == null)
            return Result<NoteResponse>.Failure(NoteErrors.NoteNotFound);

        if (note.UserId != userId)
            return Result<NoteResponse>.Failure(NoteErrors.NoteNotOwnedByUser);

        var device = await _deviceRepository.GetByIdAsync(note.SignedByDeviceId, cancellationToken);
        var deviceName = device?.Name ?? "Unknown Device";
        return Result<NoteResponse>.Success(MapToResponse(note, deviceName));
    }

    private static NoteResponse MapToResponse(dynamic note, string deviceName)
    {
        return new NoteResponse(
            note.Id,
            note.Name,
            note.SignedByDeviceId,
            deviceName,
            note.ContentType,
            ExtractContentForResponse(note.Content),
            note.CreatedAt,
            note.UpdatedAt
        );
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
