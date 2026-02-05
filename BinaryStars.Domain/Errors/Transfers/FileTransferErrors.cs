namespace BinaryStars.Domain.Errors.Transfers;

public static class FileTransferErrors
{
    public const string TransferIdCannotBeEmpty = "Transfer ID cannot be empty";
    public const string FileNameCannotBeNullOrWhitespace = "File name cannot be null or whitespace";
    public const string ContentTypeCannotBeNullOrWhitespace = "Content type cannot be null or whitespace";
    public const string FileSizeMustBePositive = "File size must be greater than zero";
    public const string SenderUserIdCannotBeEmpty = "Sender user ID cannot be empty";
    public const string TargetUserIdCannotBeEmpty = "Target user ID cannot be empty";
    public const string SenderDeviceIdCannotBeNullOrWhitespace = "Sender device ID cannot be null or whitespace";
    public const string TargetDeviceIdCannotBeNullOrWhitespace = "Target device ID cannot be null or whitespace";
    public const string TransferNotFound = "Transfer not found";
    public const string TransferExpired = "Transfer expired";
    public const string TransferNotOwnedByUser = "Transfer is not owned by the requesting user";
    public const string TransferNotForDevice = "Transfer does not target this device";
    public const string TransferAlreadyCompleted = "Transfer already completed";
    public const string TransferNotAvailable = "Transfer not available for download";
    public const string EncryptionEnvelopeMissing = "Encryption envelope missing";
}
