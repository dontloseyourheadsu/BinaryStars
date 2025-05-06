using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using System.Collections.Concurrent;
using SysColab.Shared;

var builder = WebApplication.CreateSlimBuilder(args);

// Habilitar la configuración de HTTPS en Kestrel
builder.WebHost.UseKestrelHttpsConfiguration();

// Configurar Kestrel para escuchar en HTTP y HTTPS
builder.WebHost.ConfigureKestrel(options =>
{
    options.ListenAnyIP(5268); // HTTP
    options.ListenAnyIP(7268, listenOptions =>
    {
        listenOptions.UseHttps(); // HTTPS
    });
});

// Add logging services
builder.Logging.AddConsole();
builder.Logging.SetMinimumLevel(LogLevel.Debug);

// Add CORS services
builder.Services.AddCors(options =>
{
    options.AddPolicy("AllowAll", policy =>
    {
        policy.AllowAnyOrigin()
              .AllowAnyMethod()
              .AllowAnyHeader();
    });
});

var app = builder.Build();

// Enable CORS
app.UseCors("AllowAll");

// Enable WebSocket support
app.UseWebSockets();

// Get logger from the app's service provider
var logger = app.Services.GetRequiredService<ILogger<Program>>();

var connectedDevices = new ConcurrentDictionary<Guid, (DeviceInfo DeviceInfo, WebSocket WebSocket)>();

// Temporary store for mapping pending connections by token/uuid
var pendingRegistrations = new ConcurrentDictionary<Guid, (DeviceInfo DeviceInfo, TaskCompletionSource<WebSocket> Tcs)>();

// Helper method to notify all connected devices about a device status change
async Task NotifyDeviceStatusChangeAsync(DeviceInfo deviceInfo, string messageType)
{
    logger.LogDebug("{MessageType} notification for device: {DeviceId}, Name: {DeviceName}",
        messageType, deviceInfo.Id, deviceInfo.Name);

    // Create a message to broadcast to all connected devices
    var notificationMessage = new RelayMessage
    {
        TargetId = "all", // Special value to indicate broadcasting
        MessageType = messageType,
        SerializedJson = JsonSerializer.Serialize(deviceInfo)
    };

    var notificationJson = JsonSerializer.Serialize(notificationMessage);
    var notificationBytes = Encoding.UTF8.GetBytes(notificationJson);

    // Broadcast to all connected devices except the one that triggered the event
    foreach (var device in connectedDevices)
    {
        // Skip sending to the device itself that triggered the event
        if (device.Key == deviceInfo.Id)
        {
            continue;
        }

        try
        {
            if (device.Value.WebSocket.State == WebSocketState.Open)
            {
                await device.Value.WebSocket.SendAsync(
                    new ArraySegment<byte>(notificationBytes),
                    WebSocketMessageType.Text,
                    true,
                    CancellationToken.None);

                logger.LogDebug("Sent {MessageType} notification to device: {DeviceId}",
                    messageType, device.Key);
            }
        }
        catch (Exception ex)
        {
            logger.LogError(ex, "Error sending {MessageType} notification to device: {DeviceId}",
                messageType, device.Key);
        }
    }
}

// WebSocket endpoint that waits for registration token (UUID)
app.Map("/ws", async context =>
{
    logger.LogDebug("WebSocket connection attempt received");

    // Check if the request is a WebSocket request
    if (!context.WebSockets.IsWebSocketRequest)
    {
        logger.LogWarning("Non-WebSocket request received at WebSocket endpoint");
        context.Response.StatusCode = StatusCodes.Status400BadRequest;
        return;
    }

    // Accept the WebSocket connection
    logger.LogDebug("Accepting WebSocket connection for a device");
    var webSocket = await context.WebSockets.AcceptWebSocketAsync();

    var uuidString = context.Request.Query["uuid"].ToString();
    logger.LogDebug("WebSocket connection with UUID: {UUID}", uuidString);

    var uuid = Guid.TryParse(uuidString, out var parsedUuid) ? parsedUuid : Guid.Empty;

    // Check if the UUID is valid and if the registration is pending
    if (!pendingRegistrations.TryRemove(uuid, out var pendingRegistration))
    {
        // If the UUID is not valid or not found, return an error
        logger.LogWarning("Invalid or unregistered UUID in WebSocket connection: {UUID}", uuidString);

        if (webSocket.State == WebSocketState.Open)
        {
            var errorBytes = Encoding.UTF8.GetBytes("Invalid or unregistered UUID. Must register first.");
            await webSocket.SendAsync(
                new ArraySegment<byte>(errorBytes),
                WebSocketMessageType.Text,
                true,
                CancellationToken.None);

            await webSocket.CloseAsync(
                WebSocketCloseStatus.PolicyViolation,
                "Invalid registration",
                CancellationToken.None);
        }
        return;
    }

    var tcs = pendingRegistration.Tcs;
    tcs.SetResult(webSocket);

    // Register the WebSocket with the UUID
    connectedDevices[uuid] = (pendingRegistration.DeviceInfo, webSocket);

    logger.LogInformation("Device registered and connected: {DeviceId}, Name: {DeviceName}",
        uuid, pendingRegistration.DeviceInfo.Name);

    // Notify all connected devices about the new device connection
    await NotifyDeviceStatusChangeAsync(pendingRegistration.DeviceInfo, "device_connected");

    var buffer = new byte[1024 * 4];
    var messageBuilder = new StringBuilder();

    try
    {
        while (webSocket.State == WebSocketState.Open)
        {
            // Reset the StringBuilder for a new message
            messageBuilder.Clear();
            WebSocketReceiveResult result;

            // Read potentially fragmented message
            do
            {
                result = await webSocket.ReceiveAsync(
                    new ArraySegment<byte>(buffer),
                    CancellationToken.None);

                if (result.MessageType == WebSocketMessageType.Text)
                {
                    var messageFragment = Encoding.UTF8.GetString(buffer, 0, result.Count);
                    messageBuilder.Append(messageFragment);
                }
            }
            while (!result.EndOfMessage);

            // Check if the WebSocket is closed
            if (result.MessageType == WebSocketMessageType.Close)
            {
                if (connectedDevices.TryRemove(uuid, out var removedDevice))
                {
                    // Notify all connected devices about the device disconnect
                    await NotifyDeviceStatusChangeAsync(removedDevice.DeviceInfo, "device_disconnected");
                }

                await webSocket.CloseAsync(
                    WebSocketCloseStatus.NormalClosure,
                    "Closing",
                    CancellationToken.None);
                logger.LogInformation("Device disconnected: {DeviceId}", uuid);
                break;
            }

            // Handle incoming messages
            var receivedMessage = messageBuilder.ToString();
            logger.LogDebug("Received message from {DeviceId}: {MessageLength} bytes", uuid, receivedMessage.Length);

            try
            {
                // Deserialize the message to RelayMessage
                var message = JsonSerializer.Deserialize<RelayMessage>(receivedMessage);

                if (message == null || string.IsNullOrWhiteSpace(message.TargetId) ||
                    string.IsNullOrWhiteSpace(message.SerializedJson) ||
                    string.IsNullOrWhiteSpace(message.MessageType))
                {
                    logger.LogWarning("Invalid message format received from {DeviceId}", uuid);
                    throw new Exception("Invalid message format. Must include targetId, serializedJson, and messageType.");
                }

                var targetId = Guid.Parse(message.TargetId);
                logger.LogDebug("Message relay request from {SourceId} to {TargetId} of type {MessageType}",
                    uuid, targetId, message.MessageType);

                // Relay the message to the target device
                if (connectedDevices.TryGetValue(targetId, out var targetDevice) &&
                    targetDevice.WebSocket.State == WebSocketState.Open)
                {
                    // Send the message to the target device 
                    // - keeping the original structure with messageType
                    logger.LogDebug("Relaying message from {SourceId} to {TargetId} of type {MessageType}",
                        uuid, targetId, message.MessageType);

                    // We'll send the original message as-is to preserve the messageType
                    await targetDevice.WebSocket.SendAsync(
                        new ArraySegment<byte>(Encoding.UTF8.GetBytes(receivedMessage)),
                        WebSocketMessageType.Text,
                        true,
                        CancellationToken.None);

                    logger.LogDebug("Message successfully relayed to {TargetId}", targetId);
                }
                else // if the target device is not found or offline
                {
                    // Create an error message with error messageType
                    var errorResponse = new RelayMessage
                    {
                        TargetId = uuid.ToString(), // Send back to the sender
                        MessageType = "error",
                        SerializedJson = JsonSerializer.Serialize(new
                        {
                            errorCode = "DEVICE_OFFLINE",
                            message = $"Target '{message.TargetId}' not found or offline."
                        })
                    };

                    // Send an error message back to the sender
                    logger.LogWarning("Target device {TargetId} not found or offline", targetId);
                    var errorMsg = JsonSerializer.Serialize(errorResponse);
                    await webSocket.SendAsync(
                        new ArraySegment<byte>(Encoding.UTF8.GetBytes(errorMsg)),
                        WebSocketMessageType.Text,
                        true,
                        CancellationToken.None);
                }
            }
            catch (Exception ex)
            {
                // Handle deserialization errors or other exceptions
                logger.LogError(ex, "Error processing message from {DeviceId}", uuid);

                // Create an error message
                var errorResponse = new RelayMessage
                {
                    TargetId = uuid.ToString(), // Send back to the sender
                    MessageType = "error",
                    SerializedJson = JsonSerializer.Serialize(new
                    {
                        errorCode = "PROCESSING_ERROR",
                        message = ex.Message
                    })
                };

                var errorMsg = JsonSerializer.Serialize(errorResponse);
                await webSocket.SendAsync(
                    new ArraySegment<byte>(Encoding.UTF8.GetBytes(errorMsg)),
                    WebSocketMessageType.Text,
                    true,
                    CancellationToken.None);
            }
        }
    }
    catch (Exception ex)
    {
        logger.LogError(ex, "Unexpected error in WebSocket connection for {DeviceId}", uuid);

        // Get the device info before removing it
        if (connectedDevices.TryGetValue(uuid, out var deviceBeforeRemoval))
        {
            // Remove the device from connected devices if an exception occurs
            connectedDevices.TryRemove(uuid, out _);

            // Notify all connected devices about the device disconnect
            await NotifyDeviceStatusChangeAsync(deviceBeforeRemoval.DeviceInfo, "device_disconnected");
        }

        // Attempt to close the WebSocket gracefully if it's still open
        if (webSocket.State == WebSocketState.Open)
        {
            await webSocket.CloseAsync(
                WebSocketCloseStatus.InternalServerError,
                "An unexpected error occurred",
                CancellationToken.None);
        }
    }
});

// POST /register => accepts UUID and prepares to associate with WebSocket
app.MapPost("/api/register", (DeviceInfo deviceInfo) =>
{
    logger.LogDebug("Registration attempt for device: {DeviceId}, Name: {DeviceName}", deviceInfo.Id, deviceInfo.Name);

    var tcs = new TaskCompletionSource<WebSocket>();
    if (!pendingRegistrations.TryAdd(deviceInfo.Id, (deviceInfo, tcs)))
    {
        logger.LogWarning("Failed to register device: {DeviceId}. UUID already pending or registered", deviceInfo.Id);
        return Results.BadRequest("UUID already pending or registered.");
    }

    logger.LogInformation("Device registered and pending WebSocket connection: {DeviceId}", deviceInfo.Id);
    return Results.Ok("Ready to connect WebSocket.");
});

app.MapGet("/api/connected-devices", () =>
{
    logger.LogDebug("Request received for connected devices list");
    var devices = connectedDevices.Values.Select(x => x.DeviceInfo).ToList();
    logger.LogDebug("Returning {Count} connected devices", devices.Count);

    if (devices is null or { Count: 0 })
    {
        return Results.NotFound("No connected devices found.");
    }

    // Return the list of connected devices
    return Results.Ok(devices);
});

logger.LogInformation("WebSocket server starting up");
await app.RunAsync();
logger.LogInformation("WebSocket server shutting down");