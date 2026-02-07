using System.Text.Json;
using System.Text.Json.Serialization;
using BinaryStars.Api.Models;

namespace BinaryStars.Api.Services;

public static class MessagingJson
{
    public static readonly JsonSerializerOptions SerializerOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        Converters =
        {
            new JsonStringEnumConverter()
        }
    };

    public static string SerializeEnvelope<T>(string type, T payload)
    {
        var element = JsonSerializer.SerializeToElement(payload, SerializerOptions);
        var envelope = new MessagingEnvelope(type, element);
        return JsonSerializer.Serialize(envelope, SerializerOptions);
    }

    public static MessagingEnvelope? DeserializeEnvelope(string json)
    {
        return JsonSerializer.Deserialize<MessagingEnvelope>(json, SerializerOptions);
    }

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
