using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.Configuration;

namespace BinaryStars.Tests.Integration.Fixtures;

/// <summary>
/// WebApplicationFactory that injects test configuration settings.
/// </summary>
public class TestApiFactory : WebApplicationFactory<Program>
{
    private readonly string _connectionString;
    private readonly string _kafkaBootstrapServers;
    private readonly string _tempPath;

    /// <summary>
    /// Creates a factory with test database and Kafka connection strings.
    /// </summary>
    public TestApiFactory(string connectionString, string kafkaBootstrapServers, string tempPath)
    {
        _connectionString = connectionString;
        _kafkaBootstrapServers = kafkaBootstrapServers;
        _tempPath = tempPath;
    }

    /// <summary>
    /// Overrides the web host configuration with test settings.
    /// </summary>
    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseEnvironment("Test");
        builder.ConfigureAppConfiguration((_, config) =>
        {
            var settings = new Dictionary<string, string?>
            {
                ["ConnectionStrings:DefaultConnection"] = _connectionString,
                ["Hangfire:ConnectionString"] = _connectionString,
                ["Kafka:BootstrapServers"] = _kafkaBootstrapServers,
                ["Kafka:Topic"] = "binarystars.transfers.tests",
                ["Kafka:Security:UseTls"] = "false",
                ["Kafka:Security:UseSasl"] = "false",
                ["FileTransfers:ChunkSizeBytes"] = "1024",
                ["FileTransfers:TempPath"] = _tempPath,
                ["FileTransfers:ExpiresInMinutes"] = "60"
            };

            config.AddInMemoryCollection(settings);
        });
    }
}
