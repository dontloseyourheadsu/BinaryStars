using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Reactive.Linq;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Input;
using System.IO;
using System.Runtime.InteropServices;
using Avalonia.Threading;
using BinaryStars.Models;
using BinaryStars.Services;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;

namespace BinaryStars.ViewModels;

public partial class MainViewModel : ViewModelBase
{
    private readonly IBluetoothChatService _chatService;
    private readonly IHistoryService _historyService;
    private readonly IFilePickerService _filePickerService;

    [ObservableProperty]
    private string _messageText = string.Empty;

    [ObservableProperty]
    private string _localName = "Device-" + Random.Shared.Next(1000, 9999);

    public ObservableCollection<ChatMessage> Messages { get; } = new();
    public ObservableCollection<BluetoothDeviceModel> DiscoveredDevices { get; } = new();

    [ObservableProperty]
    private bool _isScanning;

    [ObservableProperty]
    private bool _isHosting;

    [ObservableProperty]
    private bool _isConnected;

    [ObservableProperty]
    private string _status = "Ready";

    [ObservableProperty]
    private bool _isBusy;

    public string Title => "BinaryStars Chat";

    public ICommand StartScanCommand { get; }
    public ICommand StopScanCommand { get; }
    public ICommand StartHostCommand { get; }
    public ICommand StopHostCommand { get; }
    public ICommand SendCommand { get; }
    public ICommand PickFileCommand { get; }
    public ICommand ClearHistoryCommand { get; }
    public ICommand DiscoverableCommand { get; }
    public IRelayCommand<BluetoothDeviceModel> ConnectCommand { get; }

    public MainViewModel(
        IBluetoothChatService chatService,
        IHistoryService historyService,
        IFilePickerService filePickerService)
    {
        _chatService = chatService;
        _historyService = historyService;
        _filePickerService = filePickerService;

        // Setup Subscriptions
        _chatService.DiscoveredDevices
            .Subscribe(devices => Dispatcher.UIThread.Post(() =>
            {
                DiscoveredDevices.Clear();
                foreach (var d in devices) DiscoveredDevices.Add(d);
                if (IsScanning) Status = $"Scanning... Found {DiscoveredDevices.Count} devices";
            }));

        _chatService.MessageReceived
            .Subscribe(msg => Dispatcher.UIThread.Post(async () =>
            {
                Messages.Add(msg);
                await _historyService.SaveMessage(msg);
            }));

        _chatService.StatusUpdates
            .Subscribe(s => Dispatcher.UIThread.Post(() => Status = s));

        _chatService.IsScanning.Subscribe(v => IsScanning = v);
        _chatService.IsHosting.Subscribe(v => IsHosting = v);
        _chatService.IsConnected.Subscribe(v => IsConnected = v);

        // Commands
        StartScanCommand = new AsyncRelayCommand(async () => {
            Status = "Scanning...";
            await _chatService.StartScanning();
        });
        
        StopScanCommand = new AsyncRelayCommand(async () => {
            await _chatService.StopScanning();
            Status = "Scan stopped";
        });

        StartHostCommand = new AsyncRelayCommand(async () => {
            await _chatService.StartHosting(LocalName);
        });

        StopHostCommand = new AsyncRelayCommand(async () => {
            await _chatService.StopHosting();
            Status = "Hosting stopped";
        });

        SendCommand = new AsyncRelayCommand(OnSendMessage, () => !string.IsNullOrWhiteSpace(MessageText) && IsConnected);
        PickFileCommand = new AsyncRelayCommand(OnPickFile, () => IsConnected);
        
        ClearHistoryCommand = new AsyncRelayCommand(async () => {
            await _historyService.ClearHistory();
            Messages.Clear();
            Status = "History cleared";
        });

        DiscoverableCommand = new AsyncRelayCommand(async () => {
            await _chatService.MakeDiscoverable();
        });

        ConnectCommand = new AsyncRelayCommand<BluetoothDeviceModel>(OnConnect);

        _ = LoadHistory();
    }

    private async Task LoadHistory()
    {
        try 
        {
            var messages = await _historyService.GetMessages();
            Dispatcher.UIThread.Post(() => {
                foreach (var msg in messages) Messages.Add(msg);
            });
        }
        catch (Exception)
        {
            Status = "Error loading history";
        }
    }

    private async Task OnPickFile()
    {
        try
        {
            var results = await _filePickerService.PickFilesAsync();
            if (results == null || !results.Any()) return;

            foreach (var file in results)
            {
                IsBusy = true;
                Status = $"Sending {file.Name}...";
                await _chatService.SendFile(file.Name, file.Data);
                
                var msg = new ChatMessage("Me", $"Sent file: {file.Name}", DateTimeOffset.Now, true, 
                    new FileAttachment(file.Name, file.Data.Length, "application/octet-stream", IsImage: CheckIfImage(file.Name), Data: file.Data));
                
                Messages.Add(msg);
                await _historyService.SaveMessage(msg);
            }
            Status = "All files sent";
        }
        catch (Exception ex)
        {
            Status = $"File error: {ex.Message}";
        }
        finally
        {
            IsBusy = false;
        }
    }

    private bool CheckIfImage(string fileName)
    {
        var ext = Path.GetExtension(fileName).ToLowerInvariant();
        return ext == ".jpg" || ext == ".jpeg" || ext == ".png" || ext == ".gif" || ext == ".bmp" || ext == ".webp";
    }

    private async Task OnSendMessage()
    {
        if (string.IsNullOrWhiteSpace(MessageText)) return;
        try
        {
            await _chatService.SendMessage(MessageText);
            var msg = new ChatMessage("Me", MessageText, DateTimeOffset.Now, true);
            Messages.Add(msg);
            await _historyService.SaveMessage(msg);
            MessageText = string.Empty;
        }
        catch (Exception ex)
        {
            Status = $"Error: {ex.Message}";
        }
    }

    private async Task OnConnect(BluetoothDeviceModel? device)
    {
        if (device == null) return;
        IsBusy = true;
        Status = $"Connecting to {device.Name}...";
        try
        {
            await _chatService.Connect(device);
            Status = $"Connected to {device.Name}";
        }
        catch (Exception ex)
        {
            Status = $"Connection failed: {ex.Message}";
        }
        finally
        {
            IsBusy = false;
        }
    }
}
