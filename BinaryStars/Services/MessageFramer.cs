using System;
using System.IO;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace BinaryStars.Services;

/// <summary>
/// Writes/reads newline-delimited messages for reliable cross-platform interop.
/// Replaces the binary length-prefix framing with the legacy text-based protocol.
/// </summary>
public static class MessageFramer
{
    public static async Task WriteFrameAsync(Stream stream, byte[] payload, CancellationToken ct)
    {
        // For the line-based protocol, we assume the payload is a UTF-8 string (even for files, which are base64 encoded)
        // and we append a newline.
        await stream.WriteAsync(payload, ct);
        await stream.WriteAsync(new[] { (byte)'\n' }, ct);
        await stream.FlushAsync(ct);
    }

    public static async Task<byte[]?> ReadFrameAsync(Stream stream, CancellationToken ct)
    {
        using var ms = new MemoryStream();
        var buf = new byte[1];
        
        while (!ct.IsCancellationRequested)
        {
            var read = await stream.ReadAsync(buf.AsMemory(0, 1), ct);
            if (read == 0) return ms.Length > 0 ? ms.ToArray() : null;
            
            if (buf[0] == '\n')
            {
                return ms.ToArray();
            }
            
            ms.WriteByte(buf[0]);
            
            // Safety cap: 50MB for base64 files
            if (ms.Length > 50_000_000) return null;
        }
        
        return null;
    }
}
