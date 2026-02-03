using BinaryStars.Domain.Notes;

namespace BinaryStars.Application.Databases.DatabaseModels.Notes;

public class NoteDbModel
{
    public required Guid Id { get; set; }
    public required string Name { get; set; }
    public required Guid UserId { get; set; }
    public required string SignedByDeviceId { get; set; }
    public required NoteType ContentType { get; set; }

    /// <summary>
    /// Content stored as JSONB in PostgreSQL for flexible document-like storage.
    /// This allows for different content structures based on the ContentType.
    /// </summary>
    public required string Content { get; set; }

    public DateTimeOffset CreatedAt { get; set; }
    public DateTimeOffset UpdatedAt { get; set; }
}
