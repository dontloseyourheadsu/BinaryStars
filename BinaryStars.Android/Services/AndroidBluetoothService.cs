using System;
using System.Collections.Generic;
using System.IO;
using System.Reactive.Subjects;
using System.Threading;
using System.Threading.Tasks;
using Android.Bluetooth;
using Java.Util;
using BinaryStars.Models;
using BinaryStars.Services;

namespace BinaryStars.Android.Services
{
    public sealed class AndroidBluetoothService : IBluetoothService, IAsyncDisposable
    {
        private readonly Subject<BluetoothMessage> _msgs = new();
        private readonly Subject<BinaryStars.Services.ConnectionState> _state = new();

        private BluetoothAdapter? _adapter;
        private BluetoothServerSocket? _serverSocket;
        private BluetoothSocket? _socket;
        private Stream? _stream;
        private CancellationTokenSource _cts = new();
        private string? _connectedDeviceAddress;

        public IObservable<BluetoothMessage> MessageReceived => _msgs;
        public IObservable<BinaryStars.Services.ConnectionState> ConnectionStateChanged => _state;
        public string? ConnectedDeviceAddress => _connectedDeviceAddress;

        public AndroidBluetoothService()
        {
            _adapter = BluetoothAdapter.DefaultAdapter;
        }

        public async Task<IEnumerable<InTheHand.Net.Sockets.BluetoothDeviceInfo>> DiscoverDevicesAsync(CancellationToken ct = default)
        {
            _state.OnNext(BinaryStars.Services.ConnectionState.Discovering);
            try
            {
                var bc = new InTheHand.Net.Sockets.BluetoothClient();
                return await Task.Run(() => bc.DiscoverDevices(maxDevices: 20), ct);
            }
            catch { return new List<InTheHand.Net.Sockets.BluetoothDeviceInfo>(); }
            finally { _state.OnNext(BinaryStars.Services.ConnectionState.Disconnected); }
        }

        public async Task StartListeningAsync(Guid serviceUuid, CancellationToken ct = default)
        {
            Disconnect();
            _cts = new CancellationTokenSource();
            _state.OnNext(BinaryStars.Services.ConnectionState.Listening);

            if (_adapter == null || !_adapter.IsEnabled) return;

            try
            {
                var uuid = UUID.FromString(serviceUuid.ToString());
                _serverSocket = _adapter.ListenUsingRfcommWithServiceRecord("BinaryStarsSPP", uuid);
                Console.WriteLine($"[Android] Listening on {serviceUuid}...");
                
                _socket = await Task.Run(() => _serverSocket.Accept(), ct);
                _serverSocket.Close();
                _serverSocket = null;

                if (_socket != null)
                {
                    _connectedDeviceAddress = _socket.RemoteDevice?.Address;
                    _stream = _socket.InputStream;
                    _state.OnNext(BinaryStars.Services.ConnectionState.Connected);
                    _ = ReadLoopAsync(_cts.Token);
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[Android] Listen error: {ex.Message}");
                _state.OnNext(BinaryStars.Services.ConnectionState.Disconnected);
            }
        }

        public async Task ConnectAsync(InTheHand.Net.BluetoothAddress address, Guid serviceUuid, CancellationToken ct = default)
        {
            Disconnect();
            _cts = new CancellationTokenSource();
            _state.OnNext(BinaryStars.Services.ConnectionState.Connecting);

            if (_adapter == null || !_adapter.IsEnabled) return;

            try
            {
                var addrStr = address.ToString();
                if (!addrStr.Contains(":"))
                {
                    addrStr = string.Join(":", Enumerable.Range(0, 6).Select(i => addrStr.Substring(i * 2, 2)));
                }

                var device = _adapter.GetRemoteDevice(addrStr);
                var uuid = UUID.FromString(serviceUuid.ToString());
                _socket = device.CreateRfcommSocketToServiceRecord(uuid);

                Console.WriteLine($"[Android] Connecting to {addrStr}...");
                await _socket.ConnectAsync();
                
                _connectedDeviceAddress = device.Address;
                _stream = _socket.InputStream;
                _state.OnNext(BinaryStars.Services.ConnectionState.Connected);
                _ = ReadLoopAsync(_cts.Token);
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[Android] Connect error: {ex.Message}");
                _state.OnNext(BinaryStars.Services.ConnectionState.Disconnected);
                throw;
            }
        }

        public async Task SendMessageAsync(BluetoothMessage message, CancellationToken ct = default)
        {
            if (_socket == null || !_socket.IsConnected) return;
            try
            {
                var data = message.Serialize();
                await _socket.OutputStream.WriteAsync(data, 0, data.Length, ct);
                await _socket.OutputStream.WriteAsync(new[] { (byte)'\n' }, 0, 1, ct);
                await _socket.OutputStream.FlushAsync(ct);
            }
            catch { Disconnect(); }
        }

        public void Disconnect()
        {
            _cts.Cancel();
            try { _stream?.Close(); } catch { }
            _stream = null;
            try { _socket?.Close(); } catch { }
            _socket = null;
            try { _serverSocket?.Close(); } catch { }
            _serverSocket = null;
            _connectedDeviceAddress = null;
            _state.OnNext(BinaryStars.Services.ConnectionState.Disconnected);
        }

        private async Task ReadLoopAsync(CancellationToken ct)
        {
            try
            {
                while (!ct.IsCancellationRequested && _socket != null && _socket.IsConnected && _stream != null)
                {
                    var frame = await MessageFramer.ReadFrameAsync(_stream, ct);
                    if (frame == null) break;
                    _msgs.OnNext(BluetoothMessage.Deserialize(frame));
                }
            }
            catch { }
            finally { if (!ct.IsCancellationRequested) _state.OnNext(BinaryStars.Services.ConnectionState.Disconnected); }
        }

        public async ValueTask DisposeAsync()
        {
            Disconnect();
            _cts.Dispose();
            await ValueTask.CompletedTask;
        }
    }
}
