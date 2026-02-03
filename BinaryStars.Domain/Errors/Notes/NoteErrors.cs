namespace BinaryStars.Domain.Errors.Notes;

public static class NoteErrors
{
    public const string IdCannotBeEmpty = "Note ID cannot be empty";
    public const string NameCannotBeNullOrWhitespace = "Note name cannot be null or whitespace";
    public const string UserIdCannotBeEmpty = "User ID cannot be empty";
    public const string SignedByDeviceIdCannotBeNullOrWhitespace = "Signed by device ID cannot be null or whitespace";
    public const string NoteNotFound = "Note not found";
    public const string NoteNotOwnedByUser = "Note is not owned by the requesting user";
    public const string DeviceNotLinkedToUser = "Device is not linked to the user";
}
