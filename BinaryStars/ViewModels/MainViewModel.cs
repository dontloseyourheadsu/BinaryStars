using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Avalonia.Threading;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using InTheHand.Net;
using BinaryStars.Models;
using BinaryStars.Services;

namespace BinaryStars.ViewModels;

public partial class MainViewModel : ViewModelBase
{
    private readonly IDatabaseService _databaseService;
    private readonly BluetoothChatService _chatService;

    private static readonly SemaphoreSlim _loopLock = new(1, 1);
    private CancellationTokenSource? _reconnectCts;
    private CancellationTokenSource? _scanCts;
    private IDisposable? _messageSubscription;
    private IDisposable? _stateSubscription;
    private bool _sentHandshake;
    private Task? _reconnectTask;

    [ObservableProperty]
    private string _displayName = "";

    [ObservableProperty]
    private string _myDeviceId = "";

    [ObservableProperty]
    private bool _isScanning;

    [ObservableProperty]
    private bool _isConnected;

    [ObservableProperty]
    private string _statusText = "Disconnected";

    [ObservableProperty]
    private string _messageText = "";

    [ObservableProperty]
    private ChatEntity? _activeChat;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsChatsView))]
    [NotifyPropertyChangedFor(nameof(IsChatRoomView))]
    [NotifyPropertyChangedFor(nameof(IsSettingsView))]
    private string _currentView = "Chats";

    [ObservableProperty]
    private bool _showConnectionRequestPrompt;

    [ObservableProperty]
    private string _pendingRequestSenderName = "";

    [ObservableProperty]
    private string _pendingRequestAddress = "";

    [ObservableProperty]
    private string _pendingRequestId = "";

    public bool IsChatsView => CurrentView == "Chats";
    public bool IsChatRoomView => CurrentView == "ChatRoom";
    public bool IsSettingsView => CurrentView == "Settings";

    public ObservableCollection<ChatEntity> Chats { get; } = new();
    public ObservableCollection<BluetoothDeviceModel> NearbyDevices { get; } = new();
    public ObservableCollection<MessageEntity> Messages { get; } = new();

    public MainViewModel(IDatabaseService databaseService, BluetoothChatService chatService)
    {
        _databaseService = databaseService;
        _chatService = chatService;

        _ = InitializeAsync();

        _messageSubscription = _chatService.Messages.Subscribe(msg =>
        {
            Dispatcher.UIThread.Post(async () => await HandleIncomingMessageAsync(msg));
        });

        _stateSubscription = _chatService.StateChanges.Subscribe(state =>
        {
            Dispatcher.UIThread.Post(() =>
            {
                IsConnected = _chatService.IsConnected;
                switch (state)
                {
                    case ConnectionState.Disconnected:
                        StatusText = ActiveChat != null ? "Reconnecting..." : "Disconnected";
                        break;
                    case ConnectionState.Connected:
                        StatusText = "Connected";
                        _sentHandshake = false;
                        _ = SendHandshakeAsync();
                        break;
                    case ConnectionState.Listening:
                        StatusText = "Waiting for peer...";
                        break;
                    case ConnectionState.Connecting:
                        StatusText = "Connecting...";
                        break;
                    case ConnectionState.Discovering:
                        StatusText = "Scanning...";
                        break;
                }
            });
        });
    }

    private async Task InitializeAsync()
    {
        await _databaseService.InitializeAsync();
        MyDeviceId = await _databaseService.GetDeviceIdAsync();
        DisplayName = Environment.MachineName;
        await _databaseService.SetDeviceNameAsync(DisplayName);

        var isAndroid = OperatingSystem.IsAndroid();
        var targetAddr = isAndroid ? "64:49:7D:73:52:02" : "C4:EF:3D:E4:8B:8E";
        var targetName = isAndroid ? "Linux PC" : "S25 Ultra de Jesus";

        var chat = new ChatEntity { Address = targetAddr, DeviceName = targetName, DeviceId = "", LastSeen = DateTime.UtcNow };
        await _databaseService.SaveChatAsync(chat);

        await LoadChatsAsync();
        await EnterChatAsync(chat);
    }

    public async Task LoadChatsAsync()
    {
        var list = await _databaseService.GetChatsAsync();
        Chats.Clear();
        foreach (var chat in list) Chats.Add(chat);
    }

    [RelayCommand]
    public async Task ScanForDevicesAsync()
    {
        if (IsScanning) return;
        IsScanning = true;
        NearbyDevices.Clear();
        _scanCts = new CancellationTokenSource();
        try
        {
            var devices = await _chatService.DiscoverAsync(_scanCts.Token);
            foreach (var d in devices)
            {
                var id = d.DeviceAddress.ToString();
                var name = d.DeviceName ?? "Unnamed";
                if (!NearbyDevices.Any(x => x.Id == id)) NearbyDevices.Add(new BluetoothDeviceModel(id, name));
            }
        }
        catch (Exception ex) { Console.WriteLine($"Scan failed: {ex.Message}"); }
        finally { IsScanning = false; }
    }

    [RelayCommand]
    public void GoToSettings() => CurrentView = "Settings";

    [RelayCommand]
    public async Task SaveSettingsAsync()
    {
        if (!string.IsNullOrWhiteSpace(DisplayName)) await _databaseService.SetDeviceNameAsync(DisplayName);
        CurrentView = "Chats";
        StartGeneralListener();
    }

    [RelayCommand]
    public async Task SelectDeviceAsync(BluetoothDeviceModel device)
    {
        var chat = new ChatEntity { Address = device.Id, DeviceName = device.Name, DeviceId = "", LastSeen = DateTime.UtcNow };
        await _databaseService.SaveChatAsync(chat);
        await EnterChatAsync(chat);
    }

    private void StartReconnectionLoop(ChatEntity chat)
    {
        _reconnectCts?.Cancel();
        _reconnectCts = new CancellationTokenSource();
        var ct = _reconnectCts.Token;

        _reconnectTask = Task.Run(async () =>
        {
            if (!await _loopLock.WaitAsync(0)) return;
            try
            {
                if (!BluetoothAddress.TryParse(chat.Address, out var deviceAddress)) return;
                var random = new Random();
                
                while (!ct.IsCancellationRequested && !_chatService.IsConnected)
                {
                    try
                    {
                        var isListening = random.Next(2) == 0;
                        if (isListening)
                        {
                            Console.WriteLine("[Loop] Listening...");
                            using var lcts = CancellationTokenSource.CreateLinkedTokenSource(ct);
                            lcts.CancelAfter(20000);
                            await _chatService.ListenAsync(lcts.Token);
                        }
                        else
                        {
                            Console.WriteLine($"[Loop] Connecting to {deviceAddress}...");
                            using var ccts = CancellationTokenSource.CreateLinkedTokenSource(ct);
                            ccts.CancelAfter(20000);
                            await _chatService.ConnectToAsync(deviceAddress, ccts.Token);
                        }
                        if (_chatService.IsConnected) break;
                    }
                    catch { await Task.Delay(15000 + random.Next(15000), ct); }
                }
            }
            finally { _loopLock.Release(); }
        }, ct);
    }

    [RelayCommand]
    public async Task EnterChatAsync(ChatEntity chat)
    {
        ActiveChat = chat;
        CurrentView = "ChatRoom";
        StatusText = "Connecting...";
        IsConnected = false;
        _sentHandshake = false;
        Messages.Clear();
        var history = await _databaseService.GetMessagesForChatAsync(chat.Address);
        foreach (var msg in history) Messages.Add(msg);
        StartReconnectionLoop(chat);
    }

    public void StartGeneralListener()
    {
        if (ActiveChat != null) { StartReconnectionLoop(ActiveChat); return; }
        _reconnectCts?.Cancel();
        _reconnectCts = new CancellationTokenSource();
        var ct = _reconnectCts.Token;
        Task.Run(async () => {
            try { await _chatService.ListenAsync(ct); } catch { }
        }, ct);
    }

    [RelayCommand]
    public async Task AcceptConnectionRequestAsync()
    {
        ShowConnectionRequestPrompt = false;
        var chat = new ChatEntity { Address = PendingRequestAddress, DeviceName = PendingRequestSenderName, DeviceId = PendingRequestId, LastSeen = DateTime.UtcNow };
        await _databaseService.SaveChatAsync(chat);
        ActiveChat = chat;
        CurrentView = "ChatRoom";
        StatusText = "Connected";
        IsConnected = true;
        _sentHandshake = false;
        Messages.Clear();
        var history = await _databaseService.GetMessagesForChatAsync(chat.Address);
        foreach (var msg in history) Messages.Add(msg);
        await SendHandshakeAsync();
    }

    [RelayCommand]
    public void RejectConnectionRequest()
    {
        ShowConnectionRequestPrompt = false;
        _chatService.Disconnect();
        StartGeneralListener();
    }

    private async Task SendHandshakeAsync()
    {
        if (_sentHandshake || !_chatService.IsConnected) return;
        _sentHandshake = true;
        try
        {
            await _chatService.SendMessageAsync(new BluetoothMessage { Type = MessageType.Handshake, SenderId = MyDeviceId });
            Console.WriteLine($"[Handshake] IDENTIFY|{MyDeviceId} sent.");
        }
        catch { _sentHandshake = false; }
    }

    [RelayCommand]
    public async Task SendMessageAsync()
    {
        if (string.IsNullOrWhiteSpace(MessageText) || ActiveChat == null || !IsConnected) return;
        var text = MessageText;
        MessageText = "";
        try
        {
            await _chatService.SendTextAsync(text);
            var dbMsg = new MessageEntity { ChatAddress = ActiveChat.Address, Sender = DisplayName, Text = text, Timestamp = DateTimeOffset.UtcNow, IsMe = true };
            await _databaseService.SaveMessageAsync(dbMsg);
            Messages.Add(dbMsg);
        }
        catch { IsConnected = false; }
    }

    public async Task SendFileAttachmentAsync(string fileName, byte[] data)
    {
        if (ActiveChat == null || !IsConnected) return;
        var tempPath = Path.Combine(Path.GetTempPath(), fileName);
        await File.WriteAllBytesAsync(tempPath, data);
        try
        {
            await _chatService.SendFileAsync(tempPath);
            var dbMsg = new MessageEntity { ChatAddress = ActiveChat.Address, Sender = DisplayName, Text = $"Sent file: {fileName}", Timestamp = DateTimeOffset.UtcNow, IsMe = true, AttachmentName = fileName, AttachmentSize = data.Length, AttachmentType = "application/octet-stream", AttachmentData = data };
            await _databaseService.SaveMessageAsync(dbMsg);
            Messages.Add(dbMsg);
        }
        finally { if (File.Exists(tempPath)) File.Delete(tempPath); }
    }

    [RelayCommand]
    public async Task GoBackAsync()
    {
        _reconnectCts?.Cancel();
        _chatService.Disconnect();
        IsConnected = false;
        ActiveChat = null;
        CurrentView = "Chats";
        await LoadChatsAsync();
        StartGeneralListener();
    }

    private async Task HandleIncomingMessageAsync(BluetoothMessage msg)
    {
        try
        {
            if (msg.Type == MessageType.Handshake)
            {
                Console.WriteLine($"[Handshake] Received from {msg.SenderId}");
                IsConnected = true;
                if (ActiveChat == null)
                {
                    ActiveChat = new ChatEntity { Address = _chatService.ConnectedDeviceAddress ?? "00:00:00:00:00:00", DeviceName = "Peer", DeviceId = msg.SenderId ?? "", LastSeen = DateTime.UtcNow };
                }
                CurrentView = "ChatRoom";
                if (!_sentHandshake) await SendHandshakeAsync();
            }
            else if (msg.Type == MessageType.Text && ActiveChat != null)
            {
                var dbMsg = new MessageEntity { ChatAddress = ActiveChat.Address, Sender = ActiveChat.DeviceName, Text = msg.Text ?? "", Timestamp = msg.Timestamp, IsMe = false };
                await _databaseService.SaveMessageAsync(dbMsg);
                Messages.Add(dbMsg);
            }
        }
        catch { }
    }
}
