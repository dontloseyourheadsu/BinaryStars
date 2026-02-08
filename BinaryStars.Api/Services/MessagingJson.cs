using System.Text.Json;
using System.Text.Json.Serialization;
using BinaryStars.Api.Models;

namespace BinaryStars.Api.Services;

/// <summary>
/// JSON helpers for messaging envelopes.
/// </summary>
public static class MessagingJson
{
    /// <summary>
    /// Gets the serializer options used for messaging payloads.
    /// </summary>
    public static readonly JsonSerializerOptions SerializerOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        Converters =
        {
            new JsonStringEnumConverter()
        }
    };

    /// <summary>
    /// Serializes a payload into an envelope JSON string.
    /// </summary>
    /// <typeparam name="T">The payload type.</typeparam>
    /// <param name="type">The envelope type string.</param>
    /// <param name="payload">The payload to serialize.</param>
    /// <returns>The JSON string.</returns>
    public static string SerializeEnvelope<T>(string type, T payload)
    {
        var element = JsonSerializer.SerializeToElement(payload, SerializerOptions);
        var envelope = new MessagingEnvelope(type, element);
        return JsonSerializer.Serialize(envelope, SerializerOptions);
    }

    /// <summary>
    /// Deserializes an envelope JSON string.
    /// </summary>
    /// <param name="json">The JSON string.</param>
    /// <returns>The envelope or null if parsing fails.</returns>
    public static MessagingEnvelope? DeserializeEnvelope(string json)
    {
        return JsonSerializer.Deserialize<MessagingEnvelope>(json, SerializerOptions);
    }

    /// <summary>
    /// Attempts to parse a strongly typed payload from an envelope.
    /// </summary>
    /// <typeparam name="T">The payload type.</typeparam>
    /// <param name="envelope">The envelope.</param>
    /// <param name="payload">The parsed payload when successful.</param>
    /// <returns>True when parsing succeeded.</returns>
    public static bool TryReadPayload<T>(MessagingEnvelope envelope, out T? payload)
    {
        try
        {
            payload = envelope.Payload.Deserialize<T>(SerializerOptions);
            return payload != null;
        }
        catch
        {
            payload = default;
            return false;
        }
    }
}
