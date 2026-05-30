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
using BinaryStars.Models;
using BinaryStars.Services;

namespace BinaryStars.ViewModels;

public class NetworkPacket
{
    public string Type { get; set; } = ""; // "Handshake", "Text", "File"
    public string? SenderId { get; set; }
    public string? SenderName { get; set; }
    public string? Text { get; set; }
    public string? FileName { get; set; }
    public long? FileSize { get; set; }
    public string? FileType { get; set; }
    public string? FileData { get; set; } // Base64 encoded file data
}

public partial class MainViewModel : ViewModelBase
{
    private readonly IDatabaseService _databaseService;
    private readonly IBluetoothService _bluetoothService;

    private CancellationTokenSource? _connectionCts;
    private CancellationTokenSource? _scanCts;
    private IDisposable? _messageSubscription;
    private bool _sentHandshake;

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
    private string _currentView = "Chats"; // "Chats", "ChatRoom", "Settings"

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

    public MainViewModel(IDatabaseService databaseService, IBluetoothService bluetoothService)
    {
        _databaseService = databaseService;
        _bluetoothService = bluetoothService;

        _ = InitializeAsync();

        _messageSubscription = _bluetoothService.ReceivedMessages.Subscribe(line =>
        {
            Dispatcher.UIThread.Post(async () => await HandleIncomingLineAsync(line));
        });
    }

    private async Task InitializeAsync()
    {
        await _databaseService.InitializeAsync();
        MyDeviceId = await _databaseService.GetDeviceIdAsync();

        // Always take the device name directly from the system's Bluetooth name
        var systemName = _bluetoothService.GetLocalDeviceName();
        await _databaseService.SetDeviceNameAsync(systemName);
        DisplayName = systemName;

        await LoadChatsAsync();
        StartGeneralListener();
        StartPeriodicScanner();
    }

    public async Task LoadChatsAsync()
    {
        var list = await _databaseService.GetChatsAsync();
        Chats.Clear();
        foreach (var chat in list)
        {
            Chats.Add(chat);
        }
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
            var devices = await _bluetoothService.DiscoverDevicesAsync(_scanCts.Token);
            foreach (var d in devices)
            {
                if (!NearbyDevices.Any(x => x.Id == d.Id))
                {
                    NearbyDevices.Add(d);
                }
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Scan failed: {ex.Message}");
        }
        finally
        {
            IsScanning = false;
        }
    }

    [RelayCommand]
    public void GoToSettings()
    {
        CurrentView = "Settings";
    }

    [RelayCommand]
    public async Task SaveSettingsAsync()
    {
        if (!string.IsNullOrWhiteSpace(DisplayName))
        {
            await _databaseService.SetDeviceNameAsync(DisplayName);
        }
        CurrentView = "Chats";
        StartGeneralListener();
    }

    [RelayCommand]
    public async Task SelectDeviceAsync(BluetoothDeviceModel device)
    {
        var chat = new ChatEntity
        {
            Address = device.Id,
            DeviceName = device.Name,
            DeviceId = "",
            LastSeen = DateTime.UtcNow
        };
        await _databaseService.SaveChatAsync(chat);
        await EnterChatAsync(chat);
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
        foreach (var msg in history)
        {
            Messages.Add(msg);
        }

        _connectionCts?.Cancel();
        _connectionCts = new CancellationTokenSource();
        var ct = _connectionCts.Token;

        // 1. Start Server Loop: listen for incoming client matching this device address
        _ = Task.Run(async () =>
        {
            try
            {
                await _bluetoothService.StartServerAsync(chat.Address, ct);
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Server loop error: {ex.Message}");
            }
        }, ct);

        // 2. Start Client Loop: retry connecting as client to target device address
        _ = Task.Run(async () =>
        {
            while (!ct.IsCancellationRequested && !_bluetoothService.IsConnected)
            {
                try
                {
                    await _bluetoothService.ConnectAsync(chat.Address, ct);
                    // Connection succeeded! Send handshake immediately as Client
                    await SendHandshakeAsync();
                    break;
                }
                catch (Exception)
                {
                    // Delay and retry
                    await Task.Delay(3000, ct);
                }
            }
        }, ct);
    }

    public void StartGeneralListener()
    {
        _connectionCts?.Cancel();
        _connectionCts = new CancellationTokenSource();
        var ct = _connectionCts.Token;

        _ = Task.Run(async () =>
        {
            try
            {
                await _bluetoothService.StartServerAsync("", ct);
            }
            catch (Exception ex)
            {
                Console.WriteLine($"General listener error: {ex.Message}");
            }
        }, ct);
    }

    private void StartPeriodicScanner()
    {
        _ = Task.Run(async () =>
        {
            while (true)
            {
                try
                {
                    if (CurrentView == "Chats" && !IsConnected)
                    {
                        await Dispatcher.UIThread.InvokeAsync(async () =>
                        {
                            await ScanForDevicesAsync();
                        });
                    }
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"Periodic scan failed: {ex.Message}");
                }
                await Task.Delay(6000);
            }
        });
    }

    [RelayCommand]
    public async Task AcceptConnectionRequestAsync()
    {
        ShowConnectionRequestPrompt = false;

        var chat = new ChatEntity
        {
            Address = PendingRequestAddress,
            DeviceName = PendingRequestSenderName,
            DeviceId = PendingRequestId,
            LastSeen = DateTime.UtcNow
        };
        await _databaseService.SaveChatAsync(chat);

        ActiveChat = chat;
        CurrentView = "ChatRoom";
        StatusText = "Connected";
        IsConnected = true;
        _sentHandshake = false;

        Messages.Clear();
        var history = await _databaseService.GetMessagesForChatAsync(chat.Address);
        foreach (var msg in history)
        {
            Messages.Add(msg);
        }

        // Cancel general listener, reply with handshake
        _connectionCts?.Cancel();
        _connectionCts = new CancellationTokenSource();
        
        await SendHandshakeAsync();
    }

    [RelayCommand]
    public void RejectConnectionRequest()
    {
        ShowConnectionRequestPrompt = false;
        _bluetoothService.Disconnect();
        StartGeneralListener();
    }

    private async Task SendHandshakeAsync()
    {
        if (_sentHandshake) return;
        _sentHandshake = true;

        var packet = new NetworkPacket
        {
            Type = "Handshake",
            SenderId = MyDeviceId,
            SenderName = DisplayName
        };

        try
        {
            var json = JsonSerializer.Serialize(packet);
            await _bluetoothService.SendAsync(json);
            
            // If we are sending client handshake, we transition to connected once server responds.
            // If we are replying as server, we transition immediately.
            IsConnected = _bluetoothService.IsConnected;
            if (IsConnected)
            {
                StatusText = "Connected";
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Handshake failed: {ex.Message}");
            _sentHandshake = false;
        }
    }

    [RelayCommand]
    public async Task SendMessageAsync()
    {
        if (string.IsNullOrWhiteSpace(MessageText) || ActiveChat == null || !IsConnected) return;

        var text = MessageText;
        MessageText = "";

        var packet = new NetworkPacket
        {
            Type = "Text",
            SenderId = MyDeviceId,
            SenderName = DisplayName,
            Text = text
        };

        try
        {
            var json = JsonSerializer.Serialize(packet);
            await _bluetoothService.SendAsync(json);

            var dbMsg = new MessageEntity
            {
                ChatAddress = ActiveChat.Address,
                Sender = DisplayName,
                Text = text,
                Timestamp = DateTimeOffset.UtcNow,
                IsMe = true
            };

            await _databaseService.SaveMessageAsync(dbMsg);
            Messages.Add(dbMsg);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Send message failed: {ex.Message}");
            StatusText = "Reconnecting...";
            IsConnected = false;
        }
    }

    public async Task SendFileAttachmentAsync(string fileName, byte[] data)
    {
        if (ActiveChat == null || !IsConnected) return;

        var extension = Path.GetExtension(fileName).ToLower();
        var mimeType = extension switch
        {
            ".png" => "image/png",
            ".jpg" or ".jpeg" => "image/jpeg",
            ".webp" => "image/webp",
            ".gif" => "image/gif",
            _ => "application/octet-stream"
        };

        var packet = new NetworkPacket
        {
            Type = "File",
            SenderId = MyDeviceId,
            SenderName = DisplayName,
            FileName = fileName,
            FileSize = data.Length,
            FileType = mimeType,
            FileData = Convert.ToBase64String(data)
        };

        try
        {
            var json = JsonSerializer.Serialize(packet);
            await _bluetoothService.SendAsync(json);

            var dbMsg = new MessageEntity
            {
                ChatAddress = ActiveChat.Address,
                Sender = DisplayName,
                Text = $"Sent a file: {fileName}",
                Timestamp = DateTimeOffset.UtcNow,
                IsMe = true,
                AttachmentName = fileName,
                AttachmentSize = data.Length,
                AttachmentType = mimeType,
                AttachmentData = data
            };

            await _databaseService.SaveMessageAsync(dbMsg);
            Messages.Add(dbMsg);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Send file failed: {ex.Message}");
        }
    }

    [RelayCommand]
    public async Task GoBackAsync()
    {
        _connectionCts?.Cancel();
        _bluetoothService.Disconnect();
        IsConnected = false;
        StatusText = "Disconnected";
        ActiveChat = null;
        CurrentView = "Chats";
        await LoadChatsAsync();
        StartGeneralListener();
    }

    private async Task HandleIncomingLineAsync(string line)
    {
        try
        {
            var packet = JsonSerializer.Deserialize<NetworkPacket>(line);
            if (packet == null) return;

            if (packet.Type == "Handshake")
            {
                if (ActiveChat == null)
                {
                    PendingRequestSenderName = packet.SenderName ?? "Unknown Device";
                    PendingRequestAddress = _bluetoothService.ConnectedDeviceAddress ?? "";
                    PendingRequestId = packet.SenderId ?? "";
                    ShowConnectionRequestPrompt = true;
                    return;
                }

                IsConnected = true;
                StatusText = "Connected";

                // Update nickname & UUID in Chat history
                var chat = new ChatEntity
                {
                    Address = ActiveChat.Address,
                    DeviceId = packet.SenderId ?? "",
                    DeviceName = packet.SenderName ?? ActiveChat.DeviceName,
                    LastSeen = DateTime.UtcNow
                };
                await _databaseService.SaveChatAsync(chat);
                ActiveChat = chat;

                if (!_sentHandshake)
                {
                    await SendHandshakeAsync();
                }
            }
            else if (packet.Type == "Text")
            {
                var dbMsg = new MessageEntity
                {
                    ChatAddress = ActiveChat.Address,
                    Sender = packet.SenderName ?? "Other",
                    Text = packet.Text ?? "",
                    Timestamp = DateTimeOffset.UtcNow,
                    IsMe = false
                };
                await _databaseService.SaveMessageAsync(dbMsg);
                Messages.Add(dbMsg);
            }
            else if (packet.Type == "File")
            {
                byte[]? fileData = null;
                if (!string.IsNullOrEmpty(packet.FileData))
                {
                    fileData = Convert.FromBase64String(packet.FileData);
                }

                var dbMsg = new MessageEntity
                {
                    ChatAddress = ActiveChat.Address,
                    Sender = packet.SenderName ?? "Other",
                    Text = $"Received a file: {packet.FileName}",
                    Timestamp = DateTimeOffset.UtcNow,
                    IsMe = false,
                    AttachmentName = packet.FileName,
                    AttachmentSize = packet.FileSize,
                    AttachmentType = packet.FileType,
                    AttachmentData = fileData
                };
                await _databaseService.SaveMessageAsync(dbMsg);
                Messages.Add(dbMsg);
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Error processing incoming packet: {ex.Message}");
        }
    }
}
