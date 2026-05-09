using BinaryStars.Application.Databases.DatabaseModels.Messaging;

namespace BinaryStars.Application.Databases.Repositories.Messaging;

/// <summary>
/// Repository abstraction for persisted chat messages.
/// </summary>
public interface IMessageHistoryRepository
{
    /// <summary>
    /// Adds a persisted message.
    /// </summary>
    Task AddAsync(MessageHistoryDbModel message, CancellationToken cancellationToken);

    /// <summary>
    /// Gets recent messages between two devices for one user.
    /// </summary>
    Task<List<MessageHistoryDbModel>> GetConversationAsync(
        Guid userId,
        string firstDeviceId,
        string secondDeviceId,
        int limit,
        CancellationToken cancellationToken);

    /// <summary>
    /// Gets recent messages where the provided device is sender or receiver.
    /// </summary>
    Task<List<MessageHistoryDbModel>> GetByDeviceAsync(
        Guid userId,
        string deviceId,
        int limit,
        CancellationToken cancellationToken);

    /// <summary>
    /// Deletes persisted conversation rows between two devices for one user.
    /// </summary>
    Task DeleteConversationAsync(
        Guid userId,
        string firstDeviceId,
        string secondDeviceId,
        CancellationToken cancellationToken);

    /// <summary>
    /// Persists pending changes.
    /// </summary>
    Task SaveChangesAsync(CancellationToken cancellationToken);
}
