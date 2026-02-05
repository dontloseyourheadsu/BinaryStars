namespace BinaryStars.Api.Models;

public class FileTransferSettings
{
    public const string SectionName = "FileTransfers";

    public int ChunkSizeBytes { get; set; } = 524288;
    public string TempPath { get; set; } = "/tmp/binarystars-transfers";
    public int ExpiresInMinutes { get; set; } = 60;
}
