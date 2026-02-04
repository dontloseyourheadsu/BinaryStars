using BinaryStars.Application.Databases.Repositories.Notes;
using BinaryStars.Application.Mappers.Notes;
using BinaryStars.Application.Databases.Repositories.Devices;
using BinaryStars.Domain;
using BinaryStars.Domain.Notes;
using BinaryStars.Domain.Errors.Notes;
using System.Text.Json;

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
    string SignedByDeviceName,
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
    private readonly IDeviceRepository _deviceRepository;

    public NotesReadService(INotesRepository repository, IDeviceRepository deviceRepository)
    {
        _repository = repository;
        _deviceRepository = deviceRepository;
    }

    public async Task<Result<List<NoteResponse>>> GetNotesByUserAsync(Guid userId, CancellationToken cancellationToken)
    {
        var notes = await _repository.GetByUserIdAsync(userId, cancellationToken);
        var devices = await _deviceRepository.GetDevicesByUserIdAsync(userId, cancellationToken);
        var deviceLookup = devices.ToDictionary(d => d.Id, d => d.Name);
        var responses = notes.Select(note => MapToResponse(note, deviceLookup.TryGetValue(note.SignedByDeviceId, out var name) ? name : "Unknown Device")).ToList();

        return Result<List<NoteResponse>>.Success(responses);
    }

    public async Task<Result<List<NoteResponse>>> GetNotesByUserAndDeviceAsync(Guid userId, string deviceId, CancellationToken cancellationToken)
    {
        var notes = await _repository.GetByUserAndDeviceIdAsync(userId, deviceId, cancellationToken);
        var device = await _deviceRepository.GetByIdAsync(deviceId, cancellationToken);
        var deviceName = device?.Name ?? "Unknown Device";
        var responses = notes.Select(note => MapToResponse(note, deviceName)).ToList();

        return Result<List<NoteResponse>>.Success(responses);
    }

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
