using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Text.Json.Serialization;
using System.Threading.Tasks;

namespace SysColab.Shared
{
    public sealed class DeviceInfo
    {
        [JsonPropertyName("id")]
        public required Guid Id { get; set; }
        [JsonPropertyName("name")]
        public required string Name { get; set; }
        [JsonPropertyName("address")]
        public required string Address { get; set; }
        [JsonPropertyName("isPaired")]
        public bool IsPaired { get; set; }
    }
}
