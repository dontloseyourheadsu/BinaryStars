using System.Text.Json.Serialization;

namespace SysColab.Shared
{
    public record RelayMessage()
    {
        [JsonPropertyName("targetId")]
        public required string TargetId { get; init; }
        [JsonPropertyName("serializedJson")]
        public required string SerializedJson { get; init; }
        [JsonPropertyName("messageType")]
        public required string? MessageType { get; init; }
    }
}
