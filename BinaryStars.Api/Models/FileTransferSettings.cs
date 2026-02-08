namespace BinaryStars.Api.Models;

/// <summary>
/// Configuration for file transfer processing and storage.
/// </summary>
public class FileTransferSettings
{
    /// <summary>
    /// The configuration section name.
    /// </summary>
    public const string SectionName = "FileTransfers";

    /// <summary>
    /// Gets or sets the transfer chunk size in bytes.
    /// </summary>
    public int ChunkSizeBytes { get; set; } = 524288;

    /// <summary>
    /// Gets or sets the local temporary storage path for uploads.
    /// </summary>
    public string TempPath { get; set; } = "/tmp/binarystars-transfers";

    /// <summary>
    /// Gets or sets the transfer expiration window in minutes.
    /// </summary>
    public int ExpiresInMinutes { get; set; } = 60;
}
