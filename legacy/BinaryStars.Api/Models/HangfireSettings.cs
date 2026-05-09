namespace BinaryStars.Api.Models;

/// <summary>
/// Configuration for Hangfire background job storage.
/// </summary>
public class HangfireSettings
{
    /// <summary>
    /// The configuration section name.
    /// </summary>
    public const string SectionName = "Hangfire";

    /// <summary>
    /// Gets or sets the database connection string.
    /// </summary>
    public string ConnectionString { get; set; } = string.Empty;

    /// <summary>
    /// Gets or sets the schema used for Hangfire tables.
    /// </summary>
    public string Schema { get; set; } = "hangfire";
}
