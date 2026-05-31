namespace BinaryStars.Models;

public class NetworkPacket
{
    public string Type { get; set; } = ""; // "Handshake"
    public string? SenderId { get; set; }
    public string? SenderName { get; set; }
}
