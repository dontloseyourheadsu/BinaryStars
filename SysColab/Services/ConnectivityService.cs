using Microsoft.Extensions.Logging;
using SysColab.Shared;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;

namespace SysColab.Services
{
    public class ConnectivityService
    {
        private ClientWebSocket _clientWebSocket;
        private Task _receiveTask = Task.CompletedTask; // Initialize to a completed task
        private CancellationTokenSource _cancellationTokenSource;
        private readonly ILogger<ConnectivityService> _logger;

        // Event for message received
        public event EventHandler<MessageReceivedEventArgs> MessageReceived;

        // Constructor
        public ConnectivityService(ILogger<ConnectivityService> logger)
        {
            _logger = logger;
        }

        public async Task ConnectAsync(string uri)
        {
            await DisconnectAsync(); // Ensure we're disconnected first

            _clientWebSocket = new ClientWebSocket();
            _cancellationTokenSource = new CancellationTokenSource();

            try
            {
                // Make sure the URI includes the port: ws://192.168.3.7:5268/ws?uuid={deviceId}
                await _clientWebSocket.ConnectAsync(new Uri(uri), _cancellationTokenSource.Token);
                _logger.LogInformation("Connected to WebSocket server at {Uri}", uri);

                // Start the receive task as a background operation
                _receiveTask = Task.Run(() => ReceiveMessagesAsync(_cancellationTokenSource.Token));
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "WebSocket connection error");
                _clientWebSocket?.Dispose();
                _clientWebSocket = null;
                _cancellationTokenSource?.Dispose();
                _cancellationTokenSource = null;
                throw;
            }
        }

        private async Task ReceiveMessagesAsync(CancellationToken cancellationToken)
        {
            var buffer = new byte[4096];
            var messageBuilder = new StringBuilder();

            try
            {
                while (_clientWebSocket.State == WebSocketState.Open && !cancellationToken.IsCancellationRequested)
                {
                    // Reset the StringBuilder for a new message
                    messageBuilder.Clear();
                    WebSocketReceiveResult result;

                    // Read potentially fragmented message
                    do
                    {
                        result = await _clientWebSocket.ReceiveAsync(
                            new ArraySegment<byte>(buffer), cancellationToken);

                        if (result.MessageType == WebSocketMessageType.Text)
                        {
                            var messageFragment = Encoding.UTF8.GetString(buffer, 0, result.Count);
                            messageBuilder.Append(messageFragment);
                        }
                    }
                    while (!result.EndOfMessage);

                    // Handle different message types
                    if (result.MessageType == WebSocketMessageType.Close)
                    {
                        _logger.LogInformation("WebSocket server initiated closure");
                        await _clientWebSocket.CloseAsync(
                            WebSocketCloseStatus.NormalClosure,
                            "Acknowledging server close",
                            CancellationToken.None);
                        break;
                    }
                    else if (result.MessageType == WebSocketMessageType.Text)
                    {
                        var messageContent = messageBuilder.ToString();
                        _logger.LogDebug("Received WebSocket message: {Length} bytes", messageContent.Length);

                        try
                        {
                            // Try to parse as RelayMessage first (if it's coming via server relay)
                            var relayMessage = JsonSerializer.Deserialize<RelayMessage>(messageContent);

                            if (relayMessage != null)
                            {
                                _logger.LogDebug("Processed message of type: {MessageType}", relayMessage.MessageType);

                                // Trigger the event with the received message
                                OnMessageReceived(relayMessage.MessageType, relayMessage.SerializedJson);
                            }
                            else
                            {
                                // It might be a direct message (error or system message)
                                _logger.LogWarning("Received message is not a valid RelayMessage: {Message}", messageContent);
                                OnMessageReceived("system", messageContent);
                            }
                        }
                        catch (JsonException ex)
                        {
                            // It's not a valid JSON or RelayMessage
                            _logger.LogWarning(ex, "Failed to parse received message as JSON");
                            OnMessageReceived("system", messageContent);
                        }
                    }
                }
            }
            catch (OperationCanceledException)
            {
                _logger.LogInformation("WebSocket receive operation canceled");
            }
            catch (Exception ex)
            {
                if (!_cancellationTokenSource.IsCancellationRequested)
                {
                    _logger.LogError(ex, "WebSocket receive error");
                    OnMessageReceived("error", $"{{\"error\": \"{ex.Message}\"}}");
                }
            }
        }

        // Helper method to trigger the MessageReceived event
        private void OnMessageReceived(string messageType, string serializedPayload)
        {
            MessageReceived?.Invoke(this, new MessageReceivedEventArgs(messageType, serializedPayload));
        }

        // Method to send messages
        public async Task SendMessageAsync(string targetId, string messageType, object payload)
        {
            if (_clientWebSocket?.State != WebSocketState.Open)
            {
                throw new InvalidOperationException("WebSocket is not connected");
            }

            try
            {
                // Create the relay message
                var relayMessage = new RelayMessage
                {
                    TargetId = targetId,
                    MessageType = messageType,
                    SerializedJson = JsonSerializer.Serialize(payload)
                };

                // Serialize the relay message
                var messageJson = JsonSerializer.Serialize(relayMessage);
                var messageBytes = Encoding.UTF8.GetBytes(messageJson);

                _logger.LogDebug("Sending message to {TargetId} of type {MessageType}: {Length} bytes",
                    targetId, messageType, messageBytes.Length);

                await _clientWebSocket.SendAsync(
                    new ArraySegment<byte>(messageBytes),
                    WebSocketMessageType.Text,
                    true,
                    CancellationToken.None);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error sending WebSocket message");
                throw;
            }
        }

        // Method to disconnect
        public async Task DisconnectAsync()
        {
            if (_clientWebSocket != null &&
                (_clientWebSocket.State == WebSocketState.Open ||
                 _clientWebSocket.State == WebSocketState.Connecting))
            {
                try
                {
                    // Signal cancelation to stop the receive loop
                    _cancellationTokenSource?.Cancel();

                    // Send close frame
                    await _clientWebSocket.CloseAsync(
                        WebSocketCloseStatus.NormalClosure,
                        "Client disconnecting",
                        CancellationToken.None);

                    _logger.LogInformation("Disconnected from WebSocket server");
                }
                catch (Exception ex)
                {
                    _logger.LogWarning(ex, "Error during WebSocket disconnection");
                }
                finally
                {
                    // Ensure cleanup
                    _clientWebSocket.Dispose();
                    _clientWebSocket = null;
                    _cancellationTokenSource?.Dispose();
                    _cancellationTokenSource = null;
                }
            }
        }
    }

    // Event arguments class for received messages
    public class MessageReceivedEventArgs : EventArgs
    {
        public string MessageType { get; }
        public string SerializedPayload { get; }

        public MessageReceivedEventArgs(string messageType, string serializedPayload)
        {
            MessageType = messageType;
            SerializedPayload = serializedPayload;
        }
    }
}