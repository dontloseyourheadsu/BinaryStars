namespace SysColab.Shared
{
    /// <summary>
    /// Represents a file offer.
    /// </summary>
    /// <param name="FileId">File identifier.</param>
    /// <param name="Name">Name of the file.</param>
    /// <param name="Size">Size of the file in bytes.</param>
    /// <param name="SenderId">Sender device identifier.</param>
    public record FileOffer
    (
        Guid FileId,
        string Name,
        long Size,
        Guid SenderId
    );

}
