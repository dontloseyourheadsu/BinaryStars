using System.Collections.Concurrent;
using System.Net.WebSockets;
using System.Linq;

namespace BinaryStars.Api.Services;

/// <summary>
/// Represents a websocket connection tied to a device.
/// </summary>
/// <param name="DeviceId">The device identifier.</param>
/// <param name="UserId">The owning user identifier.</param>
/// <param name="Socket">The websocket instance.</param>
public record MessagingConnection(string DeviceId, Guid UserId, WebSocket Socket);

/// <summary>
/// Tracks active websocket connections for messaging.
/// </summary>
public class MessagingConnectionManager
{
    private readonly ConcurrentDictionary<string, MessagingConnection> _connections = new();

    /// <summary>
    /// Adds a new connection for a device.
    /// </summary>
    /// <param name="deviceId">The device identifier.</param>
    /// <param name="userId">The user identifier.</param>
    /// <param name="socket">The websocket instance.</param>
    /// <returns>True if added; otherwise false.</returns>
    public bool TryAdd(string deviceId, Guid userId, WebSocket socket)
    {
        return _connections.TryAdd(deviceId, new MessagingConnection(deviceId, userId, socket));
    }

    /// <summary>
    /// Removes a connection for a device.
    /// </summary>
    /// <param name="deviceId">The device identifier.</param>
    /// <returns>True if removed; otherwise false.</returns>
    public bool TryRemove(string deviceId)
    {
        return _connections.TryRemove(deviceId, out _);
    }

    /// <summary>
    /// Tries to get a connection by device identifier.
    /// </summary>
    /// <param name="deviceId">The device identifier.</param>
    /// <param name="connection">The connection when found.</param>
    /// <returns>True if found; otherwise false.</returns>
    public bool TryGet(string deviceId, out MessagingConnection? connection)
    {
        return _connections.TryGetValue(deviceId, out connection);
    }

    /// <summary>
    /// Gets all connections for the specified user.
    /// </summary>
    /// <param name="userId">The user identifier.</param>
    /// <returns>The active connections.</returns>
    public IReadOnlyCollection<MessagingConnection> GetByUser(Guid userId)
    {
        return _connections.Values.Where(c => c.UserId == userId).ToList();
    }
}
