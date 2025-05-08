using System.Text.Json.Serialization;

namespace SysColab.Shared
{
    /// <summary>
    /// Represents information about a device, including its unique identifier, name, address, and pairing status.
    /// </summary>
    public sealed class DeviceInfo
    {
        /// <summary>
        /// Gets or sets the unique identifier of the device.
        /// </summary>
        [JsonPropertyName("id")]
        public required Guid Id { get; set; }

        /// <summary>
        /// Gets or sets the name of the device.
        /// </summary>
        [JsonPropertyName("name")]
        public required string Name { get; set; }

        /// <summary>
        /// Gets or sets the address of the device.
        /// </summary>
        [JsonPropertyName("address")]
        public required string Address { get; set; }

        /// <summary>
        /// Gets or sets a value indicating whether the device is paired.
        /// </summary>
        [JsonPropertyName("isPaired")]
        public bool IsPaired { get; set; }
    }
}
