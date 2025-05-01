using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using System.Collections.Concurrent;
using SysColab.Shared;

var builder = WebApplication.CreateSlimBuilder(args);
var app = builder.Build();

// Stores connected devices: uuid => WebSocket
var connectedDevices = new ConcurrentDictionary<string, WebSocket>();

app.Map("/ws", async context =>
{
    if (!context.WebSockets.IsWebSocketRequest)
    {
        context.Response.StatusCode = StatusCodes.Status400BadRequest;
        return;
    }

    using var webSocket = await context.WebSockets.AcceptWebSocketAsync();

    // Read the first message to get the UUID
    var buffer = new byte[1024 * 4];
    var result = await webSocket.ReceiveAsync(new ArraySegment<byte>(buffer), CancellationToken.None);
    var uuid = Encoding.UTF8.GetString(buffer, 0, result.Count);

    // Register device
    connectedDevices.TryAdd(uuid, webSocket);
    Console.WriteLine($"Device connected: {uuid}");

    while (true)
    {
        result = await webSocket.ReceiveAsync(new ArraySegment<byte>(buffer), CancellationToken.None);
        if (result.MessageType == WebSocketMessageType.Close)
        {
            connectedDevices.TryRemove(uuid, out _);
            await webSocket.CloseAsync(WebSocketCloseStatus.NormalClosure, "Closing", CancellationToken.None);
            Console.WriteLine($"Device disconnected: {uuid}");
            break;
        }

        var receivedMessage = Encoding.UTF8.GetString(buffer, 0, result.Count);
        try
        {
            var message = JsonSerializer.Deserialize<RelayMessage>(receivedMessage);
            if (message == null || string.IsNullOrWhiteSpace(message.TargetId) || string.IsNullOrWhiteSpace(message.SerializedJson))
            {
                throw new Exception("Invalid message format.");
            }

            if (connectedDevices.TryGetValue(message.TargetId, out var targetSocket) && targetSocket.State == WebSocketState.Open)
            {
                var payload = Encoding.UTF8.GetBytes(message.SerializedJson);
                await targetSocket.SendAsync(new ArraySegment<byte>(payload), WebSocketMessageType.Text, true, CancellationToken.None);
            }
            else
            {
                var errorMsg = Encoding.UTF8.GetBytes($"Error: Target '{message.TargetId}' not found.");
                await webSocket.SendAsync(new ArraySegment<byte>(errorMsg), WebSocketMessageType.Text, true, CancellationToken.None);
            }
        }
        catch (Exception ex)
        {
            var errorMsg = Encoding.UTF8.GetBytes($"Error processing message: {ex.Message}");
            await webSocket.SendAsync(new ArraySegment<byte>(errorMsg), WebSocketMessageType.Text, true, CancellationToken.None);
        }
    }
});

app.Run();
