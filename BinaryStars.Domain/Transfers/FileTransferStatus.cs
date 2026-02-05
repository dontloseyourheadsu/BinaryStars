namespace BinaryStars.Domain.Transfers;

public enum FileTransferStatus
{
    Queued = 0,
    Uploading = 1,
    Available = 2,
    Downloaded = 3,
    Failed = 4,
    Expired = 5,
    Rejected = 6
}
