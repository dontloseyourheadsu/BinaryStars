using System.Text.Json.Serialization;

namespace SysColab.Shared
{
    /// <summary>
    /// Represents a message sent to a relay.
    /// </summary>
    public record RelayMessage()
    {
        /// <summary>
        /// The ID of the relay.
        /// </summary>
        [JsonPropertyName("targetId")]
        public required string TargetId { get; init; }
        [JsonPropertyName("serializedJson")]
        /// <summary>
        /// The serialized JSON message.
        /// </summary>
        public required string SerializedJson { get; init; }
        /// <summary>
        /// The type of the message.
        /// </summary>
        [JsonPropertyName("messageType")]
        public required string? MessageType { get; init; }
    }
}
