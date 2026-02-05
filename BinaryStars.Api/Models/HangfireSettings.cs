namespace BinaryStars.Api.Models;

public class HangfireSettings
{
    public const string SectionName = "Hangfire";

    public string ConnectionString { get; set; } = string.Empty;
    public string Schema { get; set; } = "hangfire";
}
