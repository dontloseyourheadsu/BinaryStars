using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reactive.Subjects;
using System.Threading;
using System.Threading.Tasks;
using Android.Bluetooth;
using Android.Content;
using BinaryStars.Models;
using BinaryStars.Services;

namespace BinaryStars.Android.Services;

public class AndroidBluetoothService : IBluetoothService
{
    private static readonly Java.Util.UUID SppUuid =
        Java.Util.UUID.FromString("00001101-0000-1000-8000-00805F9B34FB")!;

    private readonly Subject<string> _messages = new();
    public IObservable<string> ReceivedMessages => _messages;

    private readonly BluetoothAdapter _adapter = BluetoothAdapter.DefaultAdapter!;
    private BluetoothServerSocket? _serverSocket;
    private BluetoothSocket? _activeSocket;
    private StreamWriter? _writer;
    private StreamReader? _reader;

    public bool IsConnected { get; private set; }
    public string? ConnectedDeviceAddress { get; private set; }

    public static string NormalizeAddress(string? addr) =>
        addr?.Replace(":", "").Replace("-", "").Trim().ToUpperInvariant() ?? "";

    private bool IsDeviceConnected(BluetoothDevice device)
    {
        try
        {
            IntPtr classRef = global::Android.Runtime.JNIEnv.GetObjectClass(device.Handle);
            IntPtr methodId = global::Android.Runtime.JNIEnv.GetMethodID(classRef, "isConnected", "()Z");
            if (methodId != IntPtr.Zero)
            {
                return global::Android.Runtime.JNIEnv.CallBooleanMethod(device.Handle, methodId);
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[AndroidBluetoothService] JNI isConnected failed: {ex.Message}");
        }

        try
        {
            var method = device.Class.GetMethod("isConnected");
            var result = method?.Invoke(device);
            if (result is global::Java.Lang.Boolean jBool)
            {
                return jBool.BooleanValue();
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[AndroidBluetoothService] Reflection isConnected failed: {ex.Message}");
        }

        return false;
    }

    private async Task<bool> ProbeSppAsync(string address, CancellationToken ct)
    {
        BluetoothSocket? socket = null;
        try
        {
            var device = _adapter.GetRemoteDevice(address)!;
            socket = device.CreateRfcommSocketToServiceRecord(SppUuid)!;

            var connectTask = Task.Run(() => socket.Connect());
            var delayTask = Task.Delay(2000, ct);

            var completedTask = await Task.WhenAny(connectTask, delayTask);
            if (completedTask == connectTask)
            {
                await connectTask;
                
                var stream = socket.OutputStream!;
                var inStream = socket.InputStream!;
                
                var probeBytes = System.Text.Encoding.UTF8.GetBytes("{\"Type\":\"Probe\"}\n");
                await stream.WriteAsync(probeBytes, 0, probeBytes.Length, ct);
                await stream.FlushAsync(ct);
                
                var buffer = new byte[1024];
                var readTask = inStream.ReadAsync(buffer, 0, buffer.Length, ct);
                var readDelay = Task.Delay(2000, ct);
                
                var readCompleted = await Task.WhenAny(readTask, readDelay);
                if (readCompleted == readTask)
                {
                    int bytesRead = await readTask;
                    if (bytesRead > 0)
                    {
                        var line = System.Text.Encoding.UTF8.GetString(buffer, 0, bytesRead);
                        if (line.Contains("\"Type\":\"ProbeReply\""))
                        {
                            socket.Close();
                            return true;
                        }
                    }
                }
            }
            socket?.Close();
            return false;
        }
        catch
        {
            socket?.Close();
            return false;
        }
    }

    public async Task<List<BluetoothDeviceModel>> DiscoverDevicesAsync(CancellationToken ct)
    {
        var list = new List<BluetoothDeviceModel>();

        try
        {
            var bonded = _adapter.BondedDevices;
            if (bonded != null)
            {
                var tasks = new List<Task<BluetoothDeviceModel?>>();

                foreach (var d in bonded)
                {
                    if (!IsDeviceConnected(d))
                    {
                        continue;
                    }
                    var address = d.Address!;
                    var name = string.IsNullOrWhiteSpace(d.Name) ? "Unnamed Device" : d.Name;
                    Console.WriteLine($"[AndroidBluetoothService] Paired and connected device found: {name} ({address})");

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
                        list.Add(res);
                    }
                }
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[AndroidBluetoothService] Error getting bonded devices: {ex.Message}");
        }

        return list;
    }

    public string GetLocalDeviceName()
    {
        try
        {
            var name = _adapter?.Name;
            if (!string.IsNullOrEmpty(name))
            {
                return name;
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[AndroidBluetoothService] Error getting local adapter name: {ex.Message}");
        }
        string model = global::Android.OS.Build.Model;
        return $"{model} (Android)";
    }

    public async Task StartServerAsync(string targetAddress, CancellationToken ct)
    {
        Disconnect();

        while (!ct.IsCancellationRequested)
        {
            try
            {
                _serverSocket = _adapter.ListenUsingRfcommWithServiceRecord("BinaryStars", SppUuid)!;
                Console.WriteLine("[AndroidBluetoothService] Server started, listening on UUID " + SppUuid);

                while (!ct.IsCancellationRequested)
                {
                    BluetoothSocket? socket = null;
                    try
                    {
                        socket = await Task.Run(() => _serverSocket.Accept(), ct);
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[AndroidBluetoothService] Accept failed, recreating server socket: {ex.Message}");
                        socket?.Close();
                        break;
                    }

                    if (socket == null) continue;

                    // Handle connection in background
                    _ = Task.Run(async () =>
                    {
                        try
                        {
                            var remoteAddr = NormalizeAddress(socket.RemoteDevice?.Address);
                            var normalizedTarget = NormalizeAddress(targetAddress);
                            Console.WriteLine($"[AndroidBluetoothService] Server accepted socket from {remoteAddr}...");

                            if (string.IsNullOrEmpty(targetAddress) || remoteAddr == normalizedTarget)
                            {
                                var inStream = socket.InputStream!;
                                var outStream = socket.OutputStream!;
                                
                                // Read raw bytes
                                var buffer = new byte[1024];
                                var readTask = inStream.ReadAsync(buffer, 0, buffer.Length, ct);
                                var delayTask = Task.Delay(2000, ct);
                                var completed = await Task.WhenAny(readTask, delayTask);

                                if (completed == readTask)
                                {
                                    int bytesRead = await readTask;
                                    if (bytesRead > 0)
                                    {
                                        var line = System.Text.Encoding.UTF8.GetString(buffer, 0, bytesRead);
                                        Console.WriteLine($"[AndroidBluetoothService] Server received first data from {remoteAddr}: {line}");
                                        
                                        if (line.Contains("\"Type\":\"Handshake\""))
                                        {
                                            Console.WriteLine($"[AndroidBluetoothService] Valid Handshake from {remoteAddr}. Establishing connection.");
                                            var reader = new StreamReader(inStream);
                                            PrepareActiveConnection(socket, reader, line);
                                            _ = Task.Run(() => ReadLoopAsync(ct), ct);
                                            
                                            try { _serverSocket?.Close(); } catch {}
                                            return;
                                        }
                                        else if (line.Contains("\"Type\":\"Probe\""))
                                        {
                                            Console.WriteLine($"[AndroidBluetoothService] Received Probe request from {remoteAddr}, sending ProbeReply...");
                                            try
                                            {
                                                var replyBytes = System.Text.Encoding.UTF8.GetBytes("{\"Type\":\"ProbeReply\"}\n");
                                                await outStream.WriteAsync(replyBytes, 0, replyBytes.Length, ct);
                                                await outStream.FlushAsync(ct);
                                            }
                                            catch (Exception ex)
                                            {
                                                Console.WriteLine($"[AndroidBluetoothService] Error sending ProbeReply to {remoteAddr}: {ex.Message}");
                                            }
                                        }
                                    }
                                }
                            }
                            else
                            {
                                Console.WriteLine($"[AndroidBluetoothService] Connection from {remoteAddr} rejected (Target expected: {normalizedTarget}).");
                            }
                        }
                        catch (Exception ex)
                        {
                            Console.WriteLine($"[AndroidBluetoothService] Error handling client: {ex.Message}");
                        }
                        finally
                        {
                            if (!IsConnected || ConnectedDeviceAddress != NormalizeAddress(socket.RemoteDevice?.Address))
                            {
                                socket.Close();
                            }
                        }
                    }, ct);
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[AndroidBluetoothService] Server exception: {ex}");
                await Task.Delay(2000, ct);
            }
            finally
            {
                try { _serverSocket?.Close(); } catch {}
                _serverSocket = null;
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

        _adapter.CancelDiscovery();
        var device = _adapter.GetRemoteDevice(address)!;
        var socket = device.CreateRfcommSocketToServiceRecord(SppUuid)!;

        await Task.Run(() => socket.Connect(), ct);
        PrepareActiveConnection(socket);
        _ = Task.Run(() => ReadLoopAsync(ct), ct);
    }

    private void PrepareActiveConnection(BluetoothSocket socket)
    {
        _activeSocket = socket;
        _writer = new StreamWriter(socket.OutputStream!) { AutoFlush = true };
        _reader = new StreamReader(socket.InputStream!);
        ConnectedDeviceAddress = NormalizeAddress(socket.RemoteDevice?.Address ?? "");
        IsConnected = true;
    }

    private void PrepareActiveConnection(BluetoothSocket socket, StreamReader reader, string handshakeLine)
    {
        _activeSocket = socket;
        _writer = new StreamWriter(socket.OutputStream!) { AutoFlush = true };
        _reader = reader;
        ConnectedDeviceAddress = NormalizeAddress(socket.RemoteDevice?.Address ?? "");
        IsConnected = true;
        _messages.OnNext(handshakeLine);
    }

    private async Task ReadLoopAsync(CancellationToken ct)
    {
        try
        {
            while (!ct.IsCancellationRequested && IsConnected && _reader != null)
            {
                var line = await _reader.ReadLineAsync(ct);
                if (line == null) break;
                _messages.OnNext(line);
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[AndroidBluetoothService] Connection closed/lost: {ex.Message}");
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
            await _writer.WriteLineAsync(message);
        }
        else
        {
            throw new InvalidOperationException("Not connected to a remote device.");
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

        try { _activeSocket?.Close(); } catch {}
        try { _activeSocket?.Dispose(); } catch {}
        _activeSocket = null;

        try { _serverSocket?.Close(); } catch {}
        _serverSocket = null;
    }

    private class BluetoothReceiver : BroadcastReceiver
    {
        private readonly Action<BluetoothDevice> _onDeviceFound;

        public BluetoothReceiver(Action<BluetoothDevice> onDeviceFound)
        {
            _onDeviceFound = onDeviceFound;
        }

        public override void OnReceive(Context? context, Intent? intent)
        {
            if (intent?.Action == BluetoothDevice.ActionFound)
            {
                var device = (BluetoothDevice?)intent.GetParcelableExtra(BluetoothDevice.ExtraDevice);
                if (device != null)
                {
                    _onDeviceFound(device);
                }
            }
        }
    }
}
