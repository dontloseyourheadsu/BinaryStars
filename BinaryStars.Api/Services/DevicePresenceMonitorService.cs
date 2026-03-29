using System.Net.WebSockets;
using System.Text;
using BinaryStars.Api.Models;
using BinaryStars.Application.Databases.Repositories.Devices;

namespace BinaryStars.Api.Services;

/// <summary>
/// Background worker that marks stale devices offline and broadcasts presence updates.
/// </summary>
public sealed class DevicePresenceMonitorService : BackgroundService
{
    private static readonly TimeSpan SweepInterval = TimeSpan.FromSeconds(15);
    private static readonly TimeSpan PresenceTtl = TimeSpan.FromSeconds(45);

    private readonly IServiceScopeFactory _scopeFactory;
    private readonly MessagingConnectionManager _connectionManager;
    private readonly ILogger<DevicePresenceMonitorService> _logger;

    /// <summary>
    /// Initializes a new instance of the <see cref="DevicePresenceMonitorService"/> class.
    /// </summary>
    public DevicePresenceMonitorService(
        IServiceScopeFactory scopeFactory,
        MessagingConnectionManager connectionManager,
        ILogger<DevicePresenceMonitorService> logger)
    {
        _scopeFactory = scopeFactory;
        _connectionManager = connectionManager;
        _logger = logger;
    }

    /// <inheritdoc />
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                await SweepAsync(stoppingToken);
            }
            catch (OperationCanceledException) {
            _logger.LogWarning("Exception caught.");
                // Ignore cancellation.
            }
            catch (Exception exception)
            {
                _logger.LogWarning(exception, "Failed to sweep stale device presence.");
            }

            await Task.Delay(SweepInterval, stoppingToken);
        }
    }

    private async Task SweepAsync(CancellationToken cancellationToken)
    {
        using var scope = _scopeFactory.CreateScope();
        var deviceRepository = scope.ServiceProvider.GetRequiredService<IDeviceRepository>();

        var staleCutoff = DateTimeOffset.UtcNow - PresenceTtl;
        var staleDevices = await deviceRepository.GetStaleOnlineDevicesAsync(staleCutoff, cancellationToken);
        if (staleDevices.Count == 0)
        {
            return;
        }

        foreach (var device in staleDevices)
        {
            device.IsOnline = false;
        }

        await deviceRepository.SaveChangesAsync(cancellationToken);

        foreach (var device in staleDevices)
        {
            await NotifyPresenceChangedAsync(device.UserId, device.Id, false, device.LastSeen, cancellationToken);
        }
    }

    private async Task NotifyPresenceChangedAsync(Guid userId, string deviceId, bool isOnline, DateTimeOffset lastSeen, CancellationToken cancellationToken)
    {
        var presenceEvent = new DevicePresenceEvent(
            Guid.NewGuid().ToString("D"),
            userId,
            deviceId,
            isOnline,
            lastSeen,
            DateTimeOffset.UtcNow);

        var payload = MessagingJson.SerializeEnvelope("device_presence", presenceEvent);
        var bytes = Encoding.UTF8.GetBytes(payload);
        var connections = _connectionManager.GetByUser(userId);

        foreach (var connection in connections)
        {
            if (connection.Socket.State != WebSocketState.Open)
            {
                continue;
            }

            await connection.Socket.SendAsync(new ArraySegment<byte>(bytes), WebSocketMessageType.Text, true, cancellationToken);
        }
    }
}
