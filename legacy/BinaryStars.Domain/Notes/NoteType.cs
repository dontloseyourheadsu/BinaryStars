namespace BinaryStars.Domain.Notes;

/// <summary>
/// Defines the supported note content formats.
/// </summary>
public enum NoteType
{
    /// <summary>
    /// Plain text content with no formatting.
    /// </summary>
    Plaintext,

    /// <summary>
    /// Markdown content that supports basic formatting.
    /// </summary>
    Markdown
}
