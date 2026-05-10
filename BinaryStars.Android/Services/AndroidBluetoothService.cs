using System;
using System.Collections.Generic;
using System.Linq;
using System.Reactive.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Threading;
using Android.Bluetooth;
using Android.Content;
using BinaryStars.Models;
using BinaryStars.Services;
using Java.Util;

namespace BinaryStars.Android.Services;

public class AndroidBluetoothService : BluetoothChatService
{
    private readonly BluetoothAdapter? _adapter;
    private BluetoothSocket? _activeSocket;
    private BluetoothServerSocket? _serverSocket;
    private readonly List<BluetoothDeviceModel> _discoveredList = new();
    private CancellationTokenSource? _hostingCts;
    private Task? _hostingTask;

    private const string AppName = "BinaryStarsChat";
    // Standard Serial Port Profile UUID
    private static readonly UUID ChatUuid = UUID.FromString("00001101-0000-1000-8000-00805F9B34FB")!;

    public AndroidBluetoothService()
    {
        var manager = (BluetoothManager?)global::Android.App.Application.Context.GetSystemService(global::Android.Content.Context.BluetoothService);
        _adapter = manager?.Adapter;
    }

    public override async Task StartScanning()
    {
        if (_adapter == null || !_adapter.IsEnabled) throw new Exception("Bluetooth is disabled");

        await StartHosting(AppName);

        _discoveredList.Clear();
        if (_adapter.BondedDevices != null)
        {
            foreach (var device in _adapter.BondedDevices)
            {
                if (!string.IsNullOrWhiteSpace(device.Name))
                    _discoveredList.Add(new BluetoothDeviceModel(device.Address!, device.Name));
            }
        }
        OnDeviceDiscovered(_discoveredList);

        _adapter.StartDiscovery();
        SetScanning(true);
    }

    public override Task StopScanning()
    {
        _adapter?.CancelDiscovery();
        SetScanning(false);
        return Task.CompletedTask;
    }

    public override Task StartHosting(string localName)
    {
        if (_adapter == null || !_adapter.IsEnabled)
            throw new Exception("Bluetooth is disabled");

        if (_hostingTask != null && !_hostingTask.IsCompleted)
        {
            SetHosting(true);
            return Task.CompletedTask;
        }

        _hostingCts = new CancellationTokenSource();
        _hostingTask = Task.Run(() => StartHostingInternal(_hostingCts.Token));
        SetHosting(true);
        UpdateStatus("Hosting started");
        return Task.CompletedTask;
    }

    public override Task StopHosting()
    {
        _hostingCts?.Cancel();
        _hostingCts = null;

        try
        {
            _serverSocket?.Close();
            _serverSocket?.Dispose();
        }
        catch
        {
            // Ignore close failures during shutdown.
        }

        _serverSocket = null;
        SetHosting(false);
        UpdateStatus("Hosting stopped");
        return Task.CompletedTask;
    }

    public override Task MakeDiscoverable()
    {
        var intent = new Intent(BluetoothAdapter.ActionRequestDiscoverable);
        intent.PutExtra(BluetoothAdapter.ExtraDiscoverableDuration, 300);
        intent.SetFlags(ActivityFlags.NewTask);
        global::Android.App.Application.Context.StartActivity(intent);
        return Task.CompletedTask;
    }

    private async Task StartHostingInternal(CancellationToken cancellationToken)
    {
        if (_adapter == null) return;
        try
        {
            _serverSocket = _adapter.ListenUsingRfcommWithServiceRecord(AppName, ChatUuid);
            var serverSocket = _serverSocket;
            if (serverSocket == null)
            {
                throw new Exception("Could not open Bluetooth server socket");
            }

            while (!cancellationToken.IsCancellationRequested)
            {
                var socket = await serverSocket.AcceptAsync();
                if (socket != null)
                {
                    _ = HandleConnection(socket);
                }
            }
        }
        catch (Exception ex)
        {
            if (!cancellationToken.IsCancellationRequested)
            {
                UpdateStatus($"Host Error: {ex.Message}");
            }
        }
        finally
        {
            SetHosting(false);
        }
    }

    public override async Task Connect(BluetoothDeviceModel device)
    {
        if (_adapter == null) throw new Exception("No adapter");
        _adapter.CancelDiscovery();
        var nativeDevice = _adapter.GetRemoteDevice(device.Id);
        var socket = nativeDevice?.CreateRfcommSocketToServiceRecord(ChatUuid);
        if (socket == null) throw new Exception("Could not create socket");

        await socket.ConnectAsync();
        await HandleConnection(socket);
    }

    private async Task HandleConnection(BluetoothSocket socket)
    {
        _activeSocket = socket;
        SetConnected(true);
        UpdateStatus($"Connected to {socket.RemoteDevice?.Name}");

        await Task.Run(async () =>
        {
            var buffer = new byte[8192];
            try
            {
                while (_activeSocket != null && _activeSocket.IsConnected)
                {
                    int bytesRead = await _activeSocket.InputStream!.ReadAsync(buffer, 0, buffer.Length);
                    if (bytesRead > 0)
                    {
                        var text = Encoding.UTF8.GetString(buffer, 0, bytesRead);
                        OnMessageReceived(new ChatMessage("Remote", text, DateTimeOffset.Now, false));
                    }
                    else if (bytesRead <= 0) break;
                }
            }
            catch (Exception ex)
            {
                UpdateStatus($"Link Lost: {ex.Message}");
            }
            finally
            {
                socket.Dispose();
                if (_activeSocket == socket) _activeSocket = null;
                SetConnected(false);
            }
        });
    }

    public override async Task SendMessage(string text)
    {
        if (_activeSocket == null || !_activeSocket.IsConnected) throw new Exception("Not connected");
        var bytes = Encoding.UTF8.GetBytes(text);
        await _activeSocket.OutputStream!.WriteAsync(bytes, 0, bytes.Length);
    }

    public override void Dispose()
    {
        _hostingCts?.Cancel();
        _activeSocket?.Dispose();
        _serverSocket?.Dispose();
    }
}
