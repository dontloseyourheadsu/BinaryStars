using BinaryStars.Domain.Errors.Notes;

namespace BinaryStars.Domain.Notes;

/// <summary>
/// Represents a note created by a user and signed by a device.
/// Notes have immutable type (Markdown or Plaintext) and store content as JSON for flexibility.
/// </summary>
public readonly record struct Note
{
    /// <summary>
    /// Gets the unique identifier for the note.
    /// </summary>
    public Guid Id { get; init; }

    /// <summary>
    /// Gets the name/title of the note.
    /// </summary>
    public string Name { get; init; }

    /// <summary>
    /// Gets the unique identifier of the user who created the note.
    /// </summary>
    public Guid UserId { get; init; }

    /// <summary>
    /// Gets the ID of the device that signed/created this note.
    /// </summary>
    public string SignedByDeviceId { get; init; }

    /// <summary>
    /// Gets the type of note content (Markdown or Plaintext).
    /// This is immutable and cannot be changed after creation.
    /// </summary>
    public NoteType ContentType { get; init; }

    /// <summary>
    /// Gets the timestamp when the note was created.
    /// </summary>
    public DateTimeOffset CreatedAt { get; init; }

    /// <summary>
    /// Gets the timestamp when the note was last updated.
    /// </summary>
    public DateTimeOffset UpdatedAt { get; init; }

    /// <summary>
    /// Initializes a new <see cref="Note"/> with immutable content metadata.
    /// </summary>
    /// <param name="id">The note identifier.</param>
    /// <param name="name">The note title.</param>
    /// <param name="userId">The owner user identifier.</param>
    /// <param name="signedByDeviceId">The device identifier that signed the note.</param>
    /// <param name="contentType">The content type of the note.</param>
    /// <param name="createdAt">The creation timestamp.</param>
    /// <param name="updatedAt">The last updated timestamp.</param>
    /// <exception cref="ArgumentException">
    /// Thrown when required identifiers or metadata are missing.
    /// </exception>
    public Note(
        Guid id,
        string name,
        Guid userId,
        string signedByDeviceId,
        NoteType contentType,
        DateTimeOffset createdAt,
        DateTimeOffset updatedAt)
    {
        if (id == Guid.Empty) throw new ArgumentException(NoteErrors.IdCannotBeEmpty, nameof(id));
        if (string.IsNullOrWhiteSpace(name)) throw new ArgumentException(NoteErrors.NameCannotBeNullOrWhitespace, nameof(name));
        if (userId == Guid.Empty) throw new ArgumentException(NoteErrors.UserIdCannotBeEmpty, nameof(userId));
        if (string.IsNullOrWhiteSpace(signedByDeviceId)) throw new ArgumentException(NoteErrors.SignedByDeviceIdCannotBeNullOrWhitespace, nameof(signedByDeviceId));

        Id = id;
        Name = name;
        UserId = userId;
        SignedByDeviceId = signedByDeviceId;
        ContentType = contentType;
        CreatedAt = createdAt;
        UpdatedAt = updatedAt;
    }
}
