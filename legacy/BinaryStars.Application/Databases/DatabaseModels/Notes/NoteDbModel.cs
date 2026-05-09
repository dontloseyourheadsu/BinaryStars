using BinaryStars.Domain.Notes;

namespace BinaryStars.Application.Databases.DatabaseModels.Notes;

/// <summary>
/// Database entity representing a stored note.
/// </summary>
public class NoteDbModel
{
    /// <summary>
    /// Gets or sets the note identifier.
    /// </summary>
    public required Guid Id { get; set; }

    /// <summary>
    /// Gets or sets the note title.
    /// </summary>
    public required string Name { get; set; }

    /// <summary>
    /// Gets or sets the owning user identifier.
    /// </summary>
    public required Guid UserId { get; set; }

    /// <summary>
    /// Gets or sets the device identifier that signed the note.
    /// </summary>
    public required string SignedByDeviceId { get; set; }

    /// <summary>
    /// Gets or sets the note content type.
    /// </summary>
    public required NoteType ContentType { get; set; }

    /// <summary>
    /// Content stored as JSONB in PostgreSQL for flexible document-like storage.
    /// This allows for different content structures based on the ContentType.
    /// </summary>
    public required string Content { get; set; }

    /// <summary>
    /// Gets or sets the creation timestamp.
    /// </summary>
    public DateTimeOffset CreatedAt { get; set; }

    /// <summary>
    /// Gets or sets the last updated timestamp.
    /// </summary>
    public DateTimeOffset UpdatedAt { get; set; }
}
