namespace BinaryStars.Api.Services;

/// <summary>
/// Supported Kafka authentication modes.
/// </summary>
public enum KafkaAuthMode
{
    /// <summary>
    /// SASL/SCRAM authentication.
    /// </summary>
    Scram = 0,

    /// <summary>
    /// SASL/OAuth bearer authentication.
    /// </summary>
    OauthBearer = 1
}
