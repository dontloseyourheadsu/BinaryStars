using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Reactive.Subjects;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using InTheHand.Net;
using InTheHand.Net.Sockets;
using BinaryStars.Models;
using BinaryStars.Services;

namespace BinaryStars.Desktop.Services
{
    public sealed class LinuxBluetoothService : IBluetoothService, IAsyncDisposable
    {
        private readonly Subject<BluetoothMessage> _msgs = new();
        private readonly Subject<BinaryStars.Services.ConnectionState> _state = new();

        private Process? _bridgeProcess;
        private string? _connectedDeviceAddress;
        private CancellationTokenSource _cts = new();

        public IObservable<BluetoothMessage> MessageReceived => _msgs;
        public IObservable<BinaryStars.Services.ConnectionState> ConnectionStateChanged => _state;
        public string? ConnectedDeviceAddress => _connectedDeviceAddress;

        public LinuxBluetoothService()
        {
            // Hard kill any lingering bridges on startup
            KillLingeringBridges();
        }

        private void KillLingeringBridges()
        {
            try
            {
                foreach (var p in Process.GetProcessesByName("binarystars_bridge"))
                {
                    try { p.Kill(true); } catch { }
                }
            }
            catch { }
        }

        private void EnsureBridgeRunning()
        {
            if (_bridgeProcess != null && !_bridgeProcess.HasExited) return;

            var bridgePath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "binarystars_bridge");
            if (!File.Exists(bridgePath))
            {
                bridgePath = Path.GetFullPath(Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "../../../../binarystars_bridge/target/debug/binarystars_bridge"));
            }

            if (!File.Exists(bridgePath))
            {
                throw new FileNotFoundException($"Bluetooth bridge not found. Run 'cargo build' in binarystars_bridge.");
            }

            _bridgeProcess = new Process
            {
                StartInfo = new ProcessStartInfo
                {
                    FileName = bridgePath,
                    RedirectStandardInput = true,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    UseShellExecute = false,
                    CreateNoWindow = true
                }
            };

            _bridgeProcess.OutputDataReceived += (s, e) =>
            {
                if (string.IsNullOrEmpty(e.Data)) return;
                try
                {
                    var ev = JsonSerializer.Deserialize<BridgeEvent>(e.Data);
                    if (ev == null) return;

                    switch (ev.Type)
                    {
                        case "State":
                            if (Enum.TryParse<BinaryStars.Services.ConnectionState>(ev.Payload?.State, out var newState))
                                _state.OnNext(newState);
                            break;
                        case "Connected":
                            _connectedDeviceAddress = ev.Payload?.Address;
                            _state.OnNext(BinaryStars.Services.ConnectionState.Connected);
                            break;
                        case "Message":
                            if (!string.IsNullOrEmpty(ev.Payload?.DataBase64))
                            {
                                var bytes = Convert.FromBase64String(ev.Payload.DataBase64);
                                _msgs.OnNext(BluetoothMessage.Deserialize(bytes));
                            }
                            break;
                        case "Error":
                            Console.WriteLine($"[LinuxBridge] Error: {ev.Payload?.Message}");
                            _state.OnNext(BinaryStars.Services.ConnectionState.Disconnected);
                            break;
                    }
                }
                catch { }
            };

            _bridgeProcess.Start();
            _bridgeProcess.BeginOutputReadLine();
            _bridgeProcess.BeginErrorReadLine();
        }

        private void SendCommand(object cmd)
        {
            EnsureBridgeRunning();
            var json = JsonSerializer.Serialize(cmd);
            _bridgeProcess!.StandardInput.WriteLine(json);
        }

        public async Task<IEnumerable<BluetoothDeviceInfo>> DiscoverDevicesAsync(CancellationToken ct = default)
        {
            _state.OnNext(BinaryStars.Services.ConnectionState.Discovering);
            try
            {
                var bc = new BluetoothClient();
                return await Task.Run(() => bc.DiscoverDevices(maxDevices: 20), ct);
            }
            finally { _state.OnNext(BinaryStars.Services.ConnectionState.Disconnected); }
        }

        public Task StartListeningAsync(Guid serviceUuid, CancellationToken ct = default)
        {
            _connectedDeviceAddress = null;
            SendCommand(new { type = "Listen", payload = new { uuid = serviceUuid.ToString() } });
            return Task.CompletedTask;
        }

        public Task ConnectAsync(BluetoothAddress address, Guid serviceUuid, CancellationToken ct = default)
        {
            _connectedDeviceAddress = null;
            SendCommand(new { type = "Connect", payload = new { address = address.ToString(), uuid = serviceUuid.ToString() } });
            return Task.CompletedTask;
        }

        public Task SendMessageAsync(BluetoothMessage message, CancellationToken ct = default)
        {
            var data = message.Serialize();
            SendCommand(new { type = "Send", payload = new { data_base64 = Convert.ToBase64String(data) } });
            return Task.CompletedTask;
        }

        public void Disconnect()
        {
            if (_bridgeProcess != null && !_bridgeProcess.HasExited) SendCommand(new { type = "Disconnect" });
            _connectedDeviceAddress = null;
            _state.OnNext(BinaryStars.Services.ConnectionState.Disconnected);
        }

        public async ValueTask DisposeAsync()
        {
            Disconnect();
            if (_bridgeProcess != null)
            {
                try { if (!_bridgeProcess.HasExited) _bridgeProcess.Kill(true); } catch { }
                _bridgeProcess.Dispose();
            }
            _cts.Dispose();
            await ValueTask.CompletedTask;
        }

        private class BridgeEvent { public string? Type { get; set; } public BridgePayload? Payload { get; set; } }
        private class BridgePayload { public string? State { get; set; } public string? Address { get; set; } public string? DataBase64 { get; set; } public string? Message { get; set; } }
    }
}
