namespace BinaryStars.Domain.Transfers;

/// <summary>
/// Defines the lifecycle states for a file transfer.
/// </summary>
public enum FileTransferStatus
{
    /// <summary>
    /// The transfer is queued and awaiting upload.
    /// </summary>
    Queued = 0,

    /// <summary>
    /// The sender is currently uploading content.
    /// </summary>
    Uploading = 1,

    /// <summary>
    /// The transfer is available for the recipient to download.
    /// </summary>
    Available = 2,

    /// <summary>
    /// The transfer has been downloaded successfully.
    /// </summary>
    Downloaded = 3,

    /// <summary>
    /// The transfer failed due to a processing error.
    /// </summary>
    Failed = 4,

    /// <summary>
    /// The transfer expired before completion.
    /// </summary>
    Expired = 5,

    /// <summary>
    /// The recipient rejected the transfer.
    /// </summary>
    Rejected = 6
}
