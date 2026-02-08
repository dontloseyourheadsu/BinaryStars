namespace BinaryStars.Domain.Errors.Notes;

/// <summary>
/// Centralized error messages for note domain validation and access rules.
/// </summary>
public static class NoteErrors
{
    /// <summary>
    /// Indicates the note identifier is required but missing.
    /// </summary>
    public const string IdCannotBeEmpty = "Note ID cannot be empty";

    /// <summary>
    /// Indicates the note title is required but missing.
    /// </summary>
    public const string NameCannotBeNullOrWhitespace = "Note name cannot be null or whitespace";

    /// <summary>
    /// Indicates the user identifier is required but missing.
    /// </summary>
    public const string UserIdCannotBeEmpty = "User ID cannot be empty";

    /// <summary>
    /// Indicates the signing device identifier is required but missing.
    /// </summary>
    public const string SignedByDeviceIdCannotBeNullOrWhitespace = "Signed by device ID cannot be null or whitespace";

    /// <summary>
    /// Indicates a note lookup returned no record.
    /// </summary>
    public const string NoteNotFound = "Note not found";

    /// <summary>
    /// Indicates the note does not belong to the requesting user.
    /// </summary>
    public const string NoteNotOwnedByUser = "Note is not owned by the requesting user";

    /// <summary>
    /// Indicates the requested device is not linked to the user making the request.
    /// </summary>
    public const string DeviceNotLinkedToUser = "Device is not linked to the user";
}
