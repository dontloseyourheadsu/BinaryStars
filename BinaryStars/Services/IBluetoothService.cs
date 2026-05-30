using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using BinaryStars.Models;

namespace BinaryStars.Services;

public interface IBluetoothService
{
    Task StartServerAsync(string targetAddress, CancellationToken ct);
    Task ConnectAsync(string address, CancellationToken ct);
    Task SendAsync(string message);
    IObservable<string> ReceivedMessages { get; }
    Task<List<BluetoothDeviceModel>> DiscoverDevicesAsync(CancellationToken ct);
    void Disconnect();
    bool IsConnected { get; }
    string? ConnectedDeviceAddress { get; }
    string GetLocalDeviceName();
}