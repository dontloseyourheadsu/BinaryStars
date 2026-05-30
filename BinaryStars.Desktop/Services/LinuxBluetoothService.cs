using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reactive.Subjects;
using System.Threading;
using System.Threading.Tasks;
using InTheHand.Net;
using InTheHand.Net.Bluetooth;
using InTheHand.Net.Sockets;
using BinaryStars.Models;
using BinaryStars.Services;

namespace BinaryStars.Desktop.Services;

public class LinuxBluetoothService : IBluetoothService
{
    private static readonly Guid SppUuid = new("00001101-0000-1000-8000-00805F9B34FB");

    private readonly Subject<string> _receivedMessages = new();
    public IObservable<string> ReceivedMessages => _receivedMessages;

    private BluetoothListener? _listener;
    private BluetoothClient? _activeClient;
    private StreamWriter? _writer;
    private StreamReader? _reader;
    
    public bool IsConnected { get; private set; }
    public string? ConnectedDeviceAddress { get; private set; }

    public static string NormalizeAddress(string? addr) =>
        addr?.Replace(":", "").Replace("-", "").Trim().ToUpperInvariant() ?? "";

        private async Task<int> FindActiveAppChannelAsync(string address, CancellationToken ct)
    {
        Console.WriteLine($"[LinuxBluetoothService] FindActiveAppChannelAsync for {address} started (sequential)...");
        try
        {
            var deviceAddress = BluetoothAddress.Parse(address);
            // Avoid channels 4 (SAP), 5 (PBAP), 6 (MAP), 16-20 (MAP/PBAP/Sync) which trigger system alerts on Android
            var safeChannels = new[] { 1, 2, 3, 7, 8, 9, 10, 11, 12, 13, 14, 15, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30 };
            
            foreach (int channel in safeChannels)
            {
                if (ct.IsCancellationRequested)
                    return -1;

                using var client = new BluetoothClient();
                try
                {
                    var ep = new BluetoothEndPoint(deviceAddress, SppUuid, channel);
                    
                    var connectTask = Task.Run(() => client.Connect(ep), ct);
                    var delayTask = Task.Delay(1500, ct);
                    
                    var completedTask = await Task.WhenAny(connectTask, delayTask);
                    if (completedTask == connectTask)
                    {
                        await connectTask;
                        if (ct.IsCancellationRequested)
                            return -1;

                        var stream = client.GetStream();
                        var probeBytes = System.Text.Encoding.UTF8.GetBytes("{\"Type\":\"Probe\"}\n");
                        await stream.WriteAsync(probeBytes, 0, probeBytes.Length, ct);
                        await stream.FlushAsync(ct);
                        
                        var buffer = new byte[1024];
                        var readTask = stream.ReadAsync(buffer, 0, buffer.Length, ct);
                        var readDelay = Task.Delay(1500, ct);
                        
                        var readCompleted = await Task.WhenAny(readTask, readDelay);
                        if (readCompleted == readTask)
                        {
                            int bytesRead = await readTask;
                            if (bytesRead > 0)
                            {
                                var line = System.Text.Encoding.UTF8.GetString(buffer, 0, bytesRead);
                                if (!string.IsNullOrEmpty(line) && line.Contains("\"Type\":\"ProbeReply\""))
                                {
                                    Console.WriteLine($"[LinuxBluetoothService] FOUND active app on channel {channel} for {address}!");
                                    return channel;
                                }
                            }
                        }
                    }
                }
                catch (Exception ex)
                {
                    if (!ex.Message.Contains("refused") && !ex.Message.Contains("timeout") && !ex.Message.Contains("reset"))
                    {
                        Console.WriteLine($"[LinuxBluetoothService] Channel {channel} exception: {ex.Message}");
                    }
                }

                // Cooldown delay to let BlueZ release socket resources and prevent EBUSY
                await Task.Delay(1000, ct);
            }

            Console.WriteLine($"[LinuxBluetoothService] FindActiveAppChannelAsync for {address} finished. Channel: -1");
            return -1;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[LinuxBluetoothService] FindActiveAppChannelAsync error: {ex}");
            return -1;
        }
    }

    private async Task<bool> ProbeSppAsync(string address, CancellationToken ct)
    {
        int channel = await FindActiveAppChannelAsync(address, ct);
        return channel > 0;
    }

    public async Task<List<BluetoothDeviceModel>> DiscoverDevicesAsync(CancellationToken ct)
    {
        Console.WriteLine("[LinuxBluetoothService] DiscoverDevicesAsync starting scan...");
        return await Task.Run(async () =>
        {
            var list = new List<BluetoothDeviceModel>();
            try
            {
                using var client = new BluetoothClient();
                var paired = client.PairedDevices;
                var tasks = new List<Task<BluetoothDeviceModel?>>();

                foreach (var d in paired)
                {
                    if (!d.Connected)
                    {
                        continue;
                    }
                    var address = d.DeviceAddress.ToString();
                    var name = string.IsNullOrWhiteSpace(d.DeviceName) ? "Unnamed Device" : d.DeviceName;
                    Console.WriteLine($"[LinuxBluetoothService] Paired and connected device found: {name} ({address})");

                    tasks.Add(Task.Run(async () =>
                    {
                        bool running = await ProbeSppAsync(address, ct);
                        return running ? new BluetoothDeviceModel(address, name) : null;
                    }, ct));
                }

                var results = await Task.WhenAll(tasks);
                foreach (var res in results)
                {
                    if (res != null)
                    {
                        Console.WriteLine($"[LinuxBluetoothService] Discovered app running on: {res.Name} ({res.Id})");
                        list.Add(res);
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[LinuxBluetoothService] DiscoverDevices error: {ex}");
            }
            Console.WriteLine($"[LinuxBluetoothService] DiscoverDevicesAsync scan finished. Found {list.Count} active app devices.");
            return list;
        }, ct);
    }

    public string GetLocalDeviceName()
    {
        try
        {
            var radio = BluetoothRadio.Default;
            if (radio != null && !string.IsNullOrEmpty(radio.Name))
            {
                return radio.Name;
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[LinuxBluetoothService] Error getting local radio name: {ex.Message}");
        }
        return Environment.MachineName;
    }

    public async Task StartServerAsync(string targetAddress, CancellationToken ct)
    {
        Disconnect();
        
        while (!ct.IsCancellationRequested)
        {
            try
            {
                _listener = new BluetoothListener(SppUuid);
                _listener.Start();
                Console.WriteLine($"[LinuxBluetoothService] Server started, listening on UUID {SppUuid} (Target: {targetAddress ?? "Any"})...");

                while (!ct.IsCancellationRequested)
                {
                    BluetoothClient? client = null;
                    try
                    {
                        client = await Task.Run(() => _listener.AcceptBluetoothClient(), ct);
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[LinuxBluetoothService] Accept failed, recreating listener: {ex.Message}");
                        client?.Close();
                        break; // Break inner loop to recreate listener
                    }

                    if (client == null) continue;

                    // Handle connection in background
                    _ = Task.Run(async () =>
                    {
                        try
                        {
                            var remoteEndPoint = client.Client?.RemoteEndPoint as BluetoothEndPoint;
                            var remoteAddr = NormalizeAddress(remoteEndPoint?.Address?.ToString() ?? "");
                            var normalizedTarget = NormalizeAddress(targetAddress);
                            Console.WriteLine($"[LinuxBluetoothService] Server accepted socket from {remoteAddr}...");

                            if (string.IsNullOrEmpty(targetAddress) || remoteAddr == normalizedTarget)
                            {
                                var stream = client.GetStream();
                                
                                // Read raw bytes
                                var buffer = new byte[1024];
                                var readTask = stream.ReadAsync(buffer, 0, buffer.Length, ct);
                                var delayTask = Task.Delay(2000, ct);
                                var completed = await Task.WhenAny(readTask, delayTask);

                                if (completed == readTask)
                                {
                                    int bytesRead = await readTask;
                                    if (bytesRead > 0)
                                    {
                                        var line = System.Text.Encoding.UTF8.GetString(buffer, 0, bytesRead);
                                        Console.WriteLine($"[LinuxBluetoothService] Server received first data from {remoteAddr}: {line}");
                                        
                                        if (line.Contains("\"Type\":\"Handshake\""))
                                        {
                                            Console.WriteLine($"[LinuxBluetoothService] Valid Handshake from {remoteAddr}. Establishing connection.");
                                            var reader = new StreamReader(stream);
                                            PrepareActiveConnection(client, reader, line);
                                            _ = Task.Run(() => ReadLoopAsync(ct), ct);
                                            
                                            try { _listener?.Stop(); } catch {}
                                            return;
                                        }
                                        else if (line.Contains("\"Type\":\"Probe\""))
                                        {
                                            Console.WriteLine($"[LinuxBluetoothService] Received Probe request from {remoteAddr}, sending ProbeReply...");
                                            try
                                            {
                                                var replyBytes = System.Text.Encoding.UTF8.GetBytes("{\"Type\":\"ProbeReply\"}\n");
                                                await stream.WriteAsync(replyBytes, 0, replyBytes.Length, ct);
                                                await stream.FlushAsync(ct);
                                            }
                                            catch (Exception ex)
                                            {
                                                Console.WriteLine($"[LinuxBluetoothService] Error sending ProbeReply to {remoteAddr}: {ex.Message}");
                                            }
                                        }
                                    }
                                }
                            }
                            else
                            {
                                Console.WriteLine($"[LinuxBluetoothService] Connection from {remoteAddr} rejected (Target expected: {normalizedTarget}).");
                            }
                        }
                        catch (Exception ex)
                        {
                            Console.WriteLine($"[LinuxBluetoothService] Error handling client: {ex.Message}");
                        }
                        finally
                        {
                            if (!IsConnected || ConnectedDeviceAddress != NormalizeAddress((client.Client?.RemoteEndPoint as BluetoothEndPoint)?.Address?.ToString()))
                            {
                                client.Close();
                            }
                        }
                    }, ct);
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[LinuxBluetoothService] Server exception: {ex}");
                await Task.Delay(2000, ct);
            }
            finally
            {
                try { _listener?.Stop(); } catch {}
                _listener = null;
            }

            if (IsConnected)
            {
                break;
            }
        }
    }

    public async Task ConnectAsync(string address, CancellationToken ct)
    {
        Disconnect();
        
        var deviceAddress = BluetoothAddress.Parse(address);
        int channel = await FindActiveAppChannelAsync(address, ct);
        if (channel <= 0)
        {
            throw new InvalidOperationException("App is not running on target device.");
        }
        
        var client = new BluetoothClient();
        var ep = new BluetoothEndPoint(deviceAddress, SppUuid, channel);
        await Task.Run(() => client.Connect(ep), ct);
        
        var stream = client.GetStream();
        var reader = new StreamReader(stream);
        PrepareActiveConnection(client, reader);
        _ = Task.Run(() => ReadLoopAsync(ct), ct);
    }

    private void PrepareActiveConnection(BluetoothClient client)
    {
        _activeClient = client;
        var stream = client.GetStream();
        _writer = new StreamWriter(stream) { AutoFlush = true };
        _reader = new StreamReader(stream);
        var remoteEndPoint = client.Client?.RemoteEndPoint as BluetoothEndPoint;
        ConnectedDeviceAddress = NormalizeAddress(remoteEndPoint?.Address?.ToString() ?? "");
        IsConnected = true;
    }

    private void PrepareActiveConnection(BluetoothClient client, StreamReader reader)
    {
        _activeClient = client;
        var stream = client.GetStream();
        _writer = new StreamWriter(stream) { AutoFlush = true };
        _reader = reader;
        var remoteEndPoint = client.Client?.RemoteEndPoint as BluetoothEndPoint;
        ConnectedDeviceAddress = NormalizeAddress(remoteEndPoint?.Address?.ToString() ?? "");
        IsConnected = true;
    }

    private void PrepareActiveConnection(BluetoothClient client, StreamReader reader, string handshakeLine)
    {
        _activeClient = client;
        var stream = client.GetStream();
        _writer = new StreamWriter(stream) { AutoFlush = true };
        _reader = reader;
        var remoteEndPoint = client.Client?.RemoteEndPoint as BluetoothEndPoint;
        ConnectedDeviceAddress = NormalizeAddress(remoteEndPoint?.Address?.ToString() ?? "");
        IsConnected = true;
        _receivedMessages.OnNext(handshakeLine);
    }

    private async Task ReadLoopAsync(CancellationToken ct)
    {
        try
        {
            Console.WriteLine("[LinuxBluetoothService] Read loop started.");
            while (!ct.IsCancellationRequested && IsConnected && _reader != null)
            {
                var line = await _reader.ReadLineAsync(ct);
                if (line == null)
                {
                    Console.WriteLine("[LinuxBluetoothService] Read line returned null.");
                    break;
                }
                Console.WriteLine($"[LinuxBluetoothService] Received line: {line}");
                _receivedMessages.OnNext(line);
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[LinuxBluetoothService] Connection closed/lost: {ex.Message}");
        }
        finally
        {
            Disconnect();
        }
    }

    public async Task SendAsync(string message)
    {
        if (_writer != null && IsConnected)
        {
            Console.WriteLine($"[LinuxBluetoothService] Sending message: {message}");
            await _writer.WriteLineAsync(message);
        }
        else
        {
            throw new InvalidOperationException("Not connected to any device.");
        }
    }

    public void Disconnect()
    {
        IsConnected = false;
        ConnectedDeviceAddress = null;
        
        try { _writer?.Dispose(); } catch {}
        _writer = null;

        try { _reader?.Dispose(); } catch {}
        _reader = null;

        try { _activeClient?.Close(); } catch {}
        try { _activeClient?.Dispose(); } catch {}
        _activeClient = null;

        try { _listener?.Stop(); } catch {}
        _listener = null;
    }
}
