using System;
using System.IO;
using System.Text;

namespace BinaryStars.Models;

public enum MessageType : byte 
{ 
    Text = 1, 
    FileTransfer = 2, 
    Handshake = 3 
}

public sealed class BluetoothMessage
{
    public MessageType Type     { get; init; }
    public string?     Text     { get; init; }
    public string?     FileName { get; init; }
    public byte[]?     FileData { get; init; }
    public string?     SenderId { get; init; }
    public DateTimeOffset Timestamp { get; init; } = DateTimeOffset.UtcNow;

    // Protocol:
    // Handshake: IDENTIFY|senderId or IDENTIFIED|senderId
    // Text: raw string (no prefix)
    // File: FILE|filename|base64data
    public byte[] Serialize()
    {
        string payload;
        if (Type == MessageType.Handshake)
        {
            // We'll use IDENTIFY as the default handshake tag
            payload = $"IDENTIFY|{SenderId}";
        }
        else if (Type == MessageType.FileTransfer)
        {
            var base64 = Convert.ToBase64String(FileData ?? Array.Empty<byte>());
            payload = $"FILE|{FileName}|{base64}";
        }
        else
        {
            // Text - sanitize newlines to avoid protocol breaking
            payload = (Text ?? "").Replace("\n", " ").Trim();
        }
        return Encoding.UTF8.GetBytes(payload);
    }

    public static BluetoothMessage Deserialize(byte[] raw)
    {
        var line = Encoding.UTF8.GetString(raw).Trim();
        
        if (line.StartsWith("IDENTIFY|") || line.StartsWith("IDENTIFIED|"))
        {
            var parts = line.Split('|');
            return new BluetoothMessage 
            { 
                Type = MessageType.Handshake, 
                SenderId = parts.Length > 1 ? parts[1] : "",
                Text = line // Keep full line for debug
            };
        }
        else if (line.StartsWith("FILE|"))
        {
            var parts = line.Split('|', 3);
            if (parts.Length >= 3)
            {
                return new BluetoothMessage 
                { 
                    Type = MessageType.FileTransfer, 
                    FileName = parts[1], 
                    FileData = Convert.FromBase64String(parts[2]) 
                };
            }
        }
        
        // Default to Text
        return new BluetoothMessage { Type = MessageType.Text, Text = line };
    }
}
