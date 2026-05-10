using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using BinaryStars.Models;

namespace BinaryStars.Services;

public interface IBluetoothChatService
{
    IObservable<IEnumerable<BluetoothDeviceModel>> DiscoveredDevices { get; }
    IObservable<bool> IsScanning { get; }
    IObservable<bool> IsHosting { get; }
    IObservable<bool> IsConnected { get; }
    IObservable<ChatMessage> MessageReceived { get; }
    IObservable<string> StatusUpdates { get; }
    
    Task StartScanning();
    Task StopScanning();
    
    Task StartHosting(string localName);
    Task StopHosting();
    
    Task Connect(BluetoothDeviceModel device);
    Task Disconnect();
    Task MakeDiscoverable();
    
    Task SendMessage(string text);
    Task SendFile(string fileName, byte[] data);
}
