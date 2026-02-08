namespace BinaryStars.Api.Models;

/// <summary>
/// Configuration for Kafka topics and authentication.
/// </summary>
public class KafkaSettings
{
    /// <summary>
    /// The configuration section name.
    /// </summary>
    public const string SectionName = "Kafka";

    /// <summary>
    /// Gets or sets the Kafka bootstrap servers.
    /// </summary>
    public string BootstrapServers { get; set; } = string.Empty;

    /// <summary>
    /// Gets or sets the topic used for file transfers.
    /// </summary>
    public string Topic { get; set; } = "binarystars.transfers";

    /// <summary>
    /// Gets or sets the topic used for messaging.
    /// </summary>
    public string MessagingTopic { get; set; } = "binarystars.messages";

    /// <summary>
    /// Gets or sets the topic used for device removal events.
    /// </summary>
    public string DeviceRemovedTopic { get; set; } = "binarystars.device-removed";

    /// <summary>
    /// Gets or sets TLS/SASL settings.
    /// </summary>
    public KafkaSecuritySettings Security { get; set; } = new();

    /// <summary>
    /// Gets or sets SCRAM credentials.
    /// </summary>
    public KafkaScramSettings Scram { get; set; } = new();

    /// <summary>
    /// Gets or sets OAuth bearer settings.
    /// </summary>
    public KafkaOauthSettings Oauth { get; set; } = new();
}

/// <summary>
/// Kafka TLS and SASL configuration options.
/// </summary>
public class KafkaSecuritySettings
{
    /// <summary>
    /// Gets or sets whether TLS is enabled.
    /// </summary>
    public bool UseTls { get; set; } = true;

    /// <summary>
    /// Gets or sets whether SASL is enabled.
    /// </summary>
    public bool UseSasl { get; set; } = true;

    /// <summary>
    /// Gets or sets the CA certificate path.
    /// </summary>
    public string CaPath { get; set; } = string.Empty;

    /// <summary>
    /// Gets or sets the client certificate path.
    /// </summary>
    public string ClientCertPath { get; set; } = string.Empty;

    /// <summary>
    /// Gets or sets the client key path.
    /// </summary>
    public string ClientKeyPath { get; set; } = string.Empty;
}

/// <summary>
/// Kafka SCRAM credentials.
/// </summary>
public class KafkaScramSettings
{
    /// <summary>
    /// Gets or sets the SCRAM username.
    /// </summary>
    public string Username { get; set; } = string.Empty;

    /// <summary>
    /// Gets or sets the SCRAM password.
    /// </summary>
    public string Password { get; set; } = string.Empty;
}

/// <summary>
/// Kafka OAuth bearer settings.
/// </summary>
public class KafkaOauthSettings
{
    /// <summary>
    /// Gets or sets the token issuer.
    /// </summary>
    public string Issuer { get; set; } = string.Empty;

    /// <summary>
    /// Gets or sets the expected token audience.
    /// </summary>
    public string Audience { get; set; } = string.Empty;

    /// <summary>
    /// Gets or sets the token lifetime in seconds.
    /// </summary>
    public int TokenLifetimeSeconds { get; set; } = 3600;
}
