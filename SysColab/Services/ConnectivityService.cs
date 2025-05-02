using Microsoft.Extensions.Logging;
using SysColab.Constants;
using SysColab.Shared;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;

namespace SysColab.Services
{
    internal class ConnectivityService
    {
        private ClientWebSocket _clientWebSocket;
        private Task _receiveTask = Task.CompletedTask; // Initialize to a completed task
        private CancellationTokenSource _cancellationTokenSource;
        private readonly ILogger<ConnectivityService> _logger;

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

            try
            {
                while (_clientWebSocket.State == WebSocketState.Open && !cancellationToken.IsCancellationRequested)
                {
                    var result = await _clientWebSocket.ReceiveAsync(
                        new ArraySegment<byte>(buffer), cancellationToken);

                    // Process message as in my previous example...
                    // ...
                }
            }
            catch (OperationCanceledException)
            {
                _logger.LogInformation("WebSocket receive operation canceled");
            }
            catch (Exception ex)
            {
                if (!cancellationToken.IsCancellationRequested)
                {
                    _logger.LogError(ex, "WebSocket receive error");
                }
            }
        }

        // Method to send messages
        public async Task SendMessageAsync(string targetId, object payload)
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
                    SerializedJson = JsonSerializer.Serialize(payload)
                };

                // Serialize the relay message
                var messageJson = JsonSerializer.Serialize(relayMessage);
                var messageBytes = Encoding.UTF8.GetBytes(messageJson);

                _logger.LogDebug("Sending message to {TargetId}: {Length} bytes", targetId, messageBytes.Length);

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
                    _clientWebSocket.Dispose();
                    _clientWebSocket = null;
                }
            }
        }
    }
}
