using Microsoft.Extensions.Logging;
using BinaryStars.Application.Databases.DatabaseContexts;
using BinaryStars.Application.Databases.DatabaseModels.Messaging;
using Microsoft.EntityFrameworkCore;

namespace BinaryStars.Application.Databases.Repositories.Messaging;

/// <summary>
/// Entity Framework repository for persisted chat messages.
/// </summary>
public class MessageHistoryRepository : IMessageHistoryRepository
{
    private readonly ILogger<MessageHistoryRepository> _logger;

    private readonly ApplicationDbContext _context;

    /// <summary>
    /// Initializes a new instance of the <see cref="MessageHistoryRepository"/> class.
    /// </summary>
    public MessageHistoryRepository(ApplicationDbContext context, ILogger<MessageHistoryRepository> logger)
    {
        _logger = logger;

        _context = context;
    }

    /// <inheritdoc />
    public async Task AddAsync(MessageHistoryDbModel message, CancellationToken cancellationToken)
    {
        await _context.MessageHistory.AddAsync(message, cancellationToken);
    }

    /// <inheritdoc />
    public Task<List<MessageHistoryDbModel>> GetConversationAsync(
        Guid userId,
        string firstDeviceId,
        string secondDeviceId,
        int limit,
        CancellationToken cancellationToken)
    {
        var normalizedLimit = Math.Clamp(limit, 1, 500);

        return _context.MessageHistory
            .Where(m => m.UserId == userId &&
                ((m.SenderDeviceId == firstDeviceId && m.TargetDeviceId == secondDeviceId) ||
                 (m.SenderDeviceId == secondDeviceId && m.TargetDeviceId == firstDeviceId)))
            .OrderByDescending(m => m.SentAt)
            .Take(normalizedLimit)
            .ToListAsync(cancellationToken);
    }

    /// <inheritdoc />
    public Task<List<MessageHistoryDbModel>> GetByDeviceAsync(
        Guid userId,
        string deviceId,
        int limit,
        CancellationToken cancellationToken)
    {
        var normalizedLimit = Math.Clamp(limit, 1, 1000);

        return _context.MessageHistory
            .Where(m => m.UserId == userId && (m.SenderDeviceId == deviceId || m.TargetDeviceId == deviceId))
            .OrderByDescending(m => m.SentAt)
            .Take(normalizedLimit)
            .ToListAsync(cancellationToken);
    }

    /// <inheritdoc />
    public async Task DeleteConversationAsync(
        Guid userId,
        string firstDeviceId,
        string secondDeviceId,
        CancellationToken cancellationToken)
    {
        var messages = await _context.MessageHistory
            .Where(m => m.UserId == userId &&
                ((m.SenderDeviceId == firstDeviceId && m.TargetDeviceId == secondDeviceId) ||
                 (m.SenderDeviceId == secondDeviceId && m.TargetDeviceId == firstDeviceId)))
            .ToListAsync(cancellationToken);

        if (messages.Count == 0)
        {
            return;
        }

        _context.MessageHistory.RemoveRange(messages);
    }

    /// <inheritdoc />
    public Task SaveChangesAsync(CancellationToken cancellationToken)
    {
        return _context.SaveChangesAsync(cancellationToken);
    }
}
