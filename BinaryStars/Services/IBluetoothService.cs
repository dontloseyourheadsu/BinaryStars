using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using InTheHand.Net;
using InTheHand.Net.Sockets;
using BinaryStars.Models;

namespace BinaryStars.Services;

public enum ConnectionState 
{ 
    Disconnected, 
    Discovering, 
    Listening, 
    Connecting, 
    Connected 
}

public interface IBluetoothService
{
    IObservable<BluetoothMessage>  MessageReceived       { get; }
    IObservable<ConnectionState>   ConnectionStateChanged { get; }

    Task<IEnumerable<BluetoothDeviceInfo>> DiscoverDevicesAsync(
        CancellationToken ct = default);

    /// <summary>Start the RFCOMM listener (server role).</summary>
    Task StartListeningAsync(Guid serviceUuid, CancellationToken ct = default);

    /// <summary>Connect to a remote server (client role).&lt;/summary&gt;
    Task ConnectAsync(BluetoothAddress address, Guid serviceUuid,
                      CancellationToken ct = default);

    Task SendMessageAsync(BluetoothMessage message, CancellationToken ct = default);
    void Disconnect();

    string? ConnectedDeviceAddress { get; }
}
