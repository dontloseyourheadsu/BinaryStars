using BinaryStars.Api.Models;
using System.Collections.Concurrent;

namespace BinaryStars.Api.Services;

/// <summary>
/// In-memory cache of the most recent live location per user/device.
/// </summary>
public class DeviceLiveLocationCache
{
    private readonly ConcurrentDictionary<string, LocationUpdateEvent> _latestByDevice = new();

    public void Upsert(LocationUpdateEvent locationEvent)
    {
        var key = BuildKey(locationEvent.UserId, locationEvent.DeviceId);
        _latestByDevice[key] = locationEvent;
    }

    public bool TryGet(Guid userId, string deviceId, out LocationUpdateEvent? locationEvent)
    {
        return _latestByDevice.TryGetValue(BuildKey(userId, deviceId), out locationEvent);
    }

    private static string BuildKey(Guid userId, string deviceId)
    {
        return $"{userId:D}:{deviceId}";
    }
}
