using System.Collections.Concurrent;
using System.Net.WebSockets;
using System.Linq;

namespace BinaryStars.Api.Services;

public record MessagingConnection(string DeviceId, Guid UserId, WebSocket Socket);

public class MessagingConnectionManager
{
    private readonly ConcurrentDictionary<string, MessagingConnection> _connections = new();

    public bool TryAdd(string deviceId, Guid userId, WebSocket socket)
    {
        return _connections.TryAdd(deviceId, new MessagingConnection(deviceId, userId, socket));
    }

    public bool TryRemove(string deviceId)
    {
        return _connections.TryRemove(deviceId, out _);
    }

    public bool TryGet(string deviceId, out MessagingConnection? connection)
    {
        return _connections.TryGetValue(deviceId, out connection);
    }

    public IReadOnlyCollection<MessagingConnection> GetByUser(Guid userId)
    {
        return _connections.Values.Where(c => c.UserId == userId).ToList();
    }
}
