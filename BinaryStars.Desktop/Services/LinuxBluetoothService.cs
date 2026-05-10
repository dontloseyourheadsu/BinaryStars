using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Net;
using System.Net.Sockets;
using System.IO;
using System.Runtime.InteropServices;
using System.Diagnostics;
using System.Text.RegularExpressions;
using BinaryStars.Models;
using BinaryStars.Services;

namespace BinaryStars.Desktop.Services;

public class LinuxBluetoothService : BluetoothChatService
{
    private int _socketFd = -1;
    private bool _stopRead = false;
    private static readonly Regex BluetoothctlDeviceLine = new(@"^Device\s+([0-9A-Fa-f:]{17})\s+(.+)$", RegexOptions.Compiled);
    private static readonly Regex SdpChannelLine = new(@"Channel:\s*(\d+)", RegexOptions.Compiled | RegexOptions.IgnoreCase);

    // P/Invoke for libc
    [DllImport("libc.so.6", SetLastError = true)]
    private static extern int socket(int domain, int type, int protocol);

    [DllImport("libc.so.6", SetLastError = true)]
    private static extern int connect(int sockfd, ref SockAddrRfcomm addr, int addrlen);

    [DllImport("libc.so.6", SetLastError = true)]
    private static extern int read(int fd, byte[] buf, int count);

    [DllImport("libc.so.6", SetLastError = true)]
    private static extern int write(int fd, byte[] buf, int count);

    [DllImport("libc.so.6", SetLastError = true)]
    private static extern int close(int fd);

    [StructLayout(LayoutKind.Sequential, Pack = 1)]
    private struct BdAddr
    {
        [MarshalAs(UnmanagedType.ByValArray, SizeConst = 6)]
        public byte[] b;
    }

    [StructLayout(LayoutKind.Sequential, Pack = 1)]
    private struct SockAddrRfcomm
    {
        public ushort family;
        public BdAddr addr;
        public byte channel;
    }

    public override async Task StartScanning()
    {
        SetScanning(true);
        UpdateStatus("Scanning paired devices from BlueZ...");

        var devices = await Task.Run(GetPairedOrKnownDevices);
        OnDeviceDiscovered(devices);

        if (devices.Count == 0)
        {
            UpdateStatus("No known Bluetooth devices found. Pair first via bluetoothctl.");
        }
        else
        {
            UpdateStatus($"Found {devices.Count} known devices");
        }

        SetScanning(false);
    }

    public override async Task Connect(BluetoothDeviceModel device)
    {
        try
        {
            UpdateStatus($"Opening native link to {device.Id}...");

            var candidateChannels = BuildCandidateChannels(device.Id);
            var connected = false;

            foreach (var channel in candidateChannels)
            {
                var fd = socket(31, 1, 3);
                if (fd < 0)
                {
                    throw new Exception($"Native socket creation failed: {Marshal.GetLastWin32Error()}");
                }

                var addr = new SockAddrRfcomm
                {
                    family = 31,
                    channel = (byte)channel,
                    addr = new BdAddr { b = ParseBluetoothAddress(device.Id) }
                };

                var result = await Task.Run(() => connect(fd, ref addr, Marshal.SizeOf(addr)));
                if (result == 0)
                {
                    _socketFd = fd;
                    connected = true;
                    UpdateStatus($"Connected on RFCOMM channel {channel}");
                    break;
                }

                _ = close(fd);
            }

            if (!connected)
            {
                throw new Exception("Native connection failed on all tested RFCOMM channels. Ensure Android is discoverable/hosting and device is paired.");
            }

            SetConnected(true);
            UpdateStatus("CONNECTED (Native C-Bridge)");
            _stopRead = false;

            // 4. Start Read Loop
            _ = Task.Run(() =>
            {
                var buffer = new byte[4096];
                while (!_stopRead)
                {
                    int bytes = read(_socketFd, buffer, buffer.Length);
                    if (bytes > 0)
                    {
                        var text = Encoding.UTF8.GetString(buffer, 0, bytes);
                        OnMessageReceived(new ChatMessage("Remote", text, DateTimeOffset.Now, false));
                    }
                    else if (bytes <= 0)
                    {
                        break;
                    }
                }
                Disconnect().Wait();
            });
        }
        catch (Exception ex)
        {
            UpdateStatus(ex.Message);
            SetConnected(false);
            if (_socketFd != -1) close(_socketFd);
            _socketFd = -1;
        }
    }

    public override Task StartHosting(string localName)
    {
        SetHosting(false);
        UpdateStatus("Linux hosting is not implemented yet. Use Android as host and connect from Linux.");
        return Task.CompletedTask;
    }

    public override Task Disconnect()
    {
        _stopRead = true;
        if (_socketFd != -1)
        {
            close(_socketFd);
            _socketFd = -1;
        }
        SetConnected(false);
        UpdateStatus("Disconnected");
        return Task.CompletedTask;
    }

    public override async Task SendMessage(string text)
    {
        if (_socketFd < 0) throw new Exception("Link not active");
        var bytes = Encoding.UTF8.GetBytes(text);
        await Task.Run(() => write(_socketFd, bytes, bytes.Length));
    }

    public override void Dispose()
    {
        Disconnect().Wait();
    }

    private static List<BluetoothDeviceModel> GetPairedOrKnownDevices()
    {
        var devices = new List<BluetoothDeviceModel>();
        var psi = new ProcessStartInfo("bluetoothctl", "devices")
        {
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true
        };

        using var process = Process.Start(psi);
        if (process == null)
        {
            return devices;
        }

        var output = process.StandardOutput.ReadToEnd();
        process.WaitForExit();

        foreach (var line in output.Split('\n', StringSplitOptions.RemoveEmptyEntries))
        {
            var match = BluetoothctlDeviceLine.Match(line.Trim());
            if (!match.Success)
            {
                continue;
            }

            var address = match.Groups[1].Value.Trim().ToUpperInvariant();
            var name = match.Groups[2].Value.Trim();
            devices.Add(new BluetoothDeviceModel(address, name));
        }

        return devices
            .GroupBy(d => d.Id)
            .Select(g => g.First())
            .OrderBy(d => d.Name)
            .ToList();
    }

    private static byte[] ParseBluetoothAddress(string address)
    {
        var parts = address.Split(':');
        if (parts.Length != 6)
        {
            throw new ArgumentException($"Invalid Bluetooth address: {address}");
        }

        return parts.Select(x => Convert.ToByte(x, 16)).Reverse().ToArray();
    }

    private static List<int> BuildCandidateChannels(string macAddress)
    {
        var channels = new List<int>();
        var discovered = DiscoverSppChannel(macAddress);
        if (discovered.HasValue)
        {
            channels.Add(discovered.Value);
        }

        channels.Add(1);
        channels.Add(2);
        channels.Add(3);

        return channels
            .Where(c => c > 0 && c <= 30)
            .Distinct()
            .ToList();
    }

    private static int? DiscoverSppChannel(string macAddress)
    {
        try
        {
            var psi = new ProcessStartInfo("sdptool", $"browse {macAddress}")
            {
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true
            };

            using var process = Process.Start(psi);
            if (process == null)
            {
                return null;
            }

            var output = process.StandardOutput.ReadToEnd();
            process.WaitForExit();

            var matches = SdpChannelLine.Matches(output);
            foreach (Match match in matches)
            {
                if (int.TryParse(match.Groups[1].Value, out var parsed) && parsed > 0)
                {
                    return parsed;
                }
            }
        }
        catch
        {
            // sdptool may not be installed on all distributions; fallback channels are used.
        }

        return null;
    }
}
