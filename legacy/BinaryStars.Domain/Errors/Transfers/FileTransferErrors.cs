namespace BinaryStars.Domain.Errors.Transfers;

/// <summary>
/// Centralized error messages for file transfer validation and lifecycle rules.
/// </summary>
public static class FileTransferErrors
{
    /// <summary>
    /// Indicates a transfer identifier is required but missing.
    /// </summary>
    public const string TransferIdCannotBeEmpty = "Transfer ID cannot be empty";

    /// <summary>
    /// Indicates a filename is required but missing.
    /// </summary>
    public const string FileNameCannotBeNullOrWhitespace = "File name cannot be null or whitespace";

    /// <summary>
    /// Indicates a content type is required but missing.
    /// </summary>
    public const string ContentTypeCannotBeNullOrWhitespace = "Content type cannot be null or whitespace";

    /// <summary>
    /// Indicates the transfer size must be greater than zero.
    /// </summary>
    public const string FileSizeMustBePositive = "File size must be greater than zero";

    /// <summary>
    /// Indicates the sender user identifier is required but missing.
    /// </summary>
    public const string SenderUserIdCannotBeEmpty = "Sender user ID cannot be empty";

    /// <summary>
    /// Indicates the target user identifier is required but missing.
    /// </summary>
    public const string TargetUserIdCannotBeEmpty = "Target user ID cannot be empty";

    /// <summary>
    /// Indicates the sender device identifier is required but missing.
    /// </summary>
    public const string SenderDeviceIdCannotBeNullOrWhitespace = "Sender device ID cannot be null or whitespace";

    /// <summary>
    /// Indicates the target device identifier is required but missing.
    /// </summary>
    public const string TargetDeviceIdCannotBeNullOrWhitespace = "Target device ID cannot be null or whitespace";

    /// <summary>
    /// Indicates the transfer could not be found.
    /// </summary>
    public const string TransferNotFound = "Transfer not found";

    /// <summary>
    /// Indicates the transfer expired before completion.
    /// </summary>
    public const string TransferExpired = "Transfer expired";

    /// <summary>
    /// Indicates the transfer does not belong to the requesting user.
    /// </summary>
    public const string TransferNotOwnedByUser = "Transfer is not owned by the requesting user";

    /// <summary>
    /// Indicates the transfer does not target the specified device.
    /// </summary>
    public const string TransferNotForDevice = "Transfer does not target this device";

    /// <summary>
    /// Indicates the transfer already completed and cannot be modified.
    /// </summary>
    public const string TransferAlreadyCompleted = "Transfer already completed";

    /// <summary>
    /// Indicates the transfer is not in a downloadable state.
    /// </summary>
    public const string TransferNotAvailable = "Transfer not available for download";

    /// <summary>
    /// Indicates an encrypted transfer is missing its envelope metadata.
    /// </summary>
    public const string EncryptionEnvelopeMissing = "Encryption envelope missing";
}
