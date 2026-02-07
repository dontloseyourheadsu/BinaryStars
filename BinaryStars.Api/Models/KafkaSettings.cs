namespace BinaryStars.Api.Models;

public class KafkaSettings
{
    public const string SectionName = "Kafka";

    public string BootstrapServers { get; set; } = string.Empty;
    public string Topic { get; set; } = "binarystars.transfers";
    public string MessagingTopic { get; set; } = "binarystars.messages";
    public string DeviceRemovedTopic { get; set; } = "binarystars.device-removed";
    public KafkaSecuritySettings Security { get; set; } = new();
    public KafkaScramSettings Scram { get; set; } = new();
    public KafkaOauthSettings Oauth { get; set; } = new();
}

public class KafkaSecuritySettings
{
    public bool UseTls { get; set; } = true;
    public bool UseSasl { get; set; } = true;
    public string CaPath { get; set; } = string.Empty;
    public string ClientCertPath { get; set; } = string.Empty;
    public string ClientKeyPath { get; set; } = string.Empty;
}

public class KafkaScramSettings
{
    public string Username { get; set; } = string.Empty;
    public string Password { get; set; } = string.Empty;
}

public class KafkaOauthSettings
{
    public string Issuer { get; set; } = string.Empty;
    public string Audience { get; set; } = string.Empty;
    public int TokenLifetimeSeconds { get; set; } = 3600;
}
