using System;
using System.Collections.Generic;
using System.IO;
using System.Reactive.Subjects;
using System.Threading;
using System.Threading.Tasks;
using InTheHand.Net;
using InTheHand.Net.Sockets;
using BinaryStars.Models;

namespace BinaryStars.Services;

public sealed class BluetoothChatService
{
    // Shared service UUID — using standard SPP for better platform compatibility
    public static readonly Guid ServiceUuid = InTheHand.Net.Bluetooth.BluetoothService.SerialPort;

    private readonly IBluetoothService _bt;
    private readonly Subject<BluetoothMessage> _messages = new();
    private ConnectionState _lastState = ConnectionState.Disconnected;

    public IObservable<BluetoothMessage> Messages    => _messages;
    public IObservable<ConnectionState>  StateChanges => _bt.ConnectionStateChanged;
    public bool IsConnected => _lastState == ConnectionState.Connected;
    public string? ConnectedDeviceAddress => _bt.ConnectedDeviceAddress;

    public BluetoothChatService(IBluetoothService bluetoothService)
    {
        _bt = bluetoothService;
        _bt.MessageReceived.Subscribe(m => _messages.OnNext(m));
        _bt.ConnectionStateChanged.Subscribe(s => _lastState = s);
    }

    public Task<IEnumerable<BluetoothDeviceInfo>> DiscoverAsync(CancellationToken ct = default)
        => _bt.DiscoverDevicesAsync(ct);

    public Task ListenAsync(CancellationToken ct = default)
        => _bt.StartListeningAsync(ServiceUuid, ct);

    public Task ConnectToAsync(BluetoothAddress address, CancellationToken ct = default)
        => _bt.ConnectAsync(address, ServiceUuid, ct);

    public Task SendTextAsync(string text, CancellationToken ct = default)
        => _bt.SendMessageAsync(new BluetoothMessage { Type = MessageType.Text, Text = text }, ct);

    public async Task SendFileAsync(string filePath, CancellationToken ct = default)
    {
        var data = await File.ReadAllBytesAsync(filePath, ct);
        await _bt.SendMessageAsync(new BluetoothMessage
        {
            Type     = MessageType.FileTransfer,
            FileName = Path.GetFileName(filePath),
            FileData = data
        }, ct);
    }

    public Task SendMessageAsync(BluetoothMessage message, CancellationToken ct = default)
        => _bt.SendMessageAsync(message, ct);

    public void Disconnect() => _bt.Disconnect();
}
