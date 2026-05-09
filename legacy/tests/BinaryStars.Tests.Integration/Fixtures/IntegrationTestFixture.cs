using BinaryStars.Application.Databases.DatabaseContexts;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Testcontainers.Kafka;
using Testcontainers.PostgreSql;

namespace BinaryStars.Tests.Integration.Fixtures;

/// <summary>
/// Manages shared containers and API host for integration tests.
/// </summary>
public sealed class IntegrationTestFixture : IAsyncLifetime
{
    private readonly PostgreSqlContainer _postgres;
    private readonly KafkaContainer _kafka;
    private TestApiFactory? _factory;

    /// <summary>
    /// Initializes the fixture with PostgreSQL and Kafka containers.
    /// </summary>
    public IntegrationTestFixture()
    {
        _postgres = new PostgreSqlBuilder()
            .WithDatabase("binarystars_tests")
            .WithUsername("postgres")
            .WithPassword("postgres")
            .Build();

        _kafka = new KafkaBuilder()
            .WithImage("confluentinc/cp-kafka:7.6.1")
            .Build();

        TempPath = Path.Combine(Path.GetTempPath(), "binarystars-tests", Guid.NewGuid().ToString("N"));
    }

    /// <summary>
    /// Temporary storage path for file transfer tests.
    /// </summary>
    public string TempPath { get; }

    /// <summary>
    /// The API factory used to create test clients.
    /// </summary>
    public TestApiFactory Factory => _factory ?? throw new InvalidOperationException("Factory not initialized.");

    /// <summary>
    /// Service provider for the running API host.
    /// </summary>
    public IServiceProvider Services => Factory.Services;

    /// <summary>
    /// Starts containers, configures the API host, and seeds the database.
    /// </summary>
    public async Task InitializeAsync()
    {
        Directory.CreateDirectory(TempPath);
        await _postgres.StartAsync();
        await _kafka.StartAsync();

        _factory = new TestApiFactory(
            _postgres.GetConnectionString(),
            _kafka.GetBootstrapAddress(),
            TempPath);

        using (var scope = _factory.Services.CreateScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
            await db.Database.EnsureCreatedAsync();
        }

        _ = _factory.CreateClient();
    }

    /// <summary>
    /// Disposes containers and cleans temporary data.
    /// </summary>
    public async Task DisposeAsync()
    {
        _factory?.Dispose();
        _factory = null;

        try
        {
            if (Directory.Exists(TempPath))
            {
                Directory.Delete(TempPath, true);
            }
        }
        catch (IOException)
        {
        }

        await _kafka.DisposeAsync();
        await _postgres.DisposeAsync();
    }
}
