using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Text.Json.Serialization;
using System.Threading.Tasks;

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
