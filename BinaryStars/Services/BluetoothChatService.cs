using System;
using System.Collections.Generic;
using System.Linq;
using System.Reactive.Linq;
using System.Reactive.Subjects;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;
using System.IO;
using BinaryStars.Models;

namespace BinaryStars.Services;

public class BluetoothChatService : IBluetoothChatService, IDisposable
{
    private const string ServiceUuid = "7f7f0001-7f7f-7f7f-7f7f-7f7f7f7f7f7f";
    private const string CharacteristicUuid = "7f7f0002-7f7f-7f7f-7f7f-7f7f7f7f7f7f";
    
    private const byte TypeText = 0x01;
    private const byte TypeFileStart = 0x02;
    private const byte TypeFileChunk = 0x03;
    private const byte TypeFileEnd = 0x04;

    private readonly BehaviorSubject<IEnumerable<BluetoothDeviceModel>> _discoveredDevices = new(Enumerable.Empty<BluetoothDeviceModel>());
    private readonly BehaviorSubject<bool> _isScanning = new(false);
    private readonly BehaviorSubject<bool> _isHosting = new(false);
    private readonly BehaviorSubject<bool> _isConnected = new(false);
    private readonly Subject<ChatMessage> _messageReceived = new();
    private readonly Subject<string> _statusUpdates = new();

    public IObservable<IEnumerable<BluetoothDeviceModel>> DiscoveredDevices => _discoveredDevices.AsObservable();
    public IObservable<bool> IsScanning => _isScanning.AsObservable();
    public IObservable<bool> IsHosting => _isHosting.AsObservable();
    public IObservable<bool> IsConnected => _isConnected.AsObservable();
    public IObservable<ChatMessage> MessageReceived => _messageReceived.AsObservable();
    public IObservable<string> StatusUpdates => _statusUpdates.AsObservable();

    public virtual Task StartScanning() => Task.FromException(new NotImplementedException());
    public virtual Task StopScanning() => Task.CompletedTask;
    public virtual Task StartHosting(string localName) => Task.FromException(new NotImplementedException());
    public virtual Task StopHosting() => Task.CompletedTask;
    public virtual Task Connect(BluetoothDeviceModel device) => Task.FromException(new NotImplementedException());
    public virtual Task Disconnect() => Task.CompletedTask;
    public virtual Task MakeDiscoverable() => Task.CompletedTask;
    public virtual Task SendMessage(string text) => Task.FromException(new NotImplementedException());
    public virtual Task SendFile(string fileName, byte[] data) => Task.FromException(new NotImplementedException());

    protected void OnMessageReceived(ChatMessage msg) => _messageReceived.OnNext(msg);
    protected void OnDeviceDiscovered(IEnumerable<BluetoothDeviceModel> devices) => _discoveredDevices.OnNext(devices);
    protected void SetScanning(bool scanning) => _isScanning.OnNext(scanning);
    protected void SetHosting(bool hosting) => _isHosting.OnNext(hosting);
    protected void SetConnected(bool connected) => _isConnected.OnNext(connected);
    protected void UpdateStatus(string status) => _statusUpdates.OnNext(status);

    public virtual void Dispose() { }
}
