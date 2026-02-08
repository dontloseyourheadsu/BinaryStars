using BinaryStars.Api.Extensions;
using BinaryStars.Api.Models;
using BinaryStars.Api.Services;
using Hangfire;
using Hangfire.PostgreSql;
using BinaryStars.Application.Databases.DatabaseContexts;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Serilog;
using Serilog.Sinks.Grafana.Loki;
using Scalar.AspNetCore;
using System.Text;
using System.Text.Json.Serialization;
using BinaryStars.Api.Middleware;

var builder = WebApplication.CreateBuilder(args);

// Configure Serilog
builder.Host.UseSerilog((context, configuration) =>
{
    configuration
        .ReadFrom.Configuration(context.Configuration)
        .Enrich.FromLogContext()
        .WriteTo.Console();

    var lokiUrl = context.Configuration["Serilog:LokiUrl"];
    if (!string.IsNullOrEmpty(lokiUrl))
    {
        configuration.WriteTo.GrafanaLoki(lokiUrl);
    }
});

// Add services to the container.
builder.Services.AddDatabaseServices(builder.Configuration);
builder.Services.AddApplicationServices();

builder.Services.Configure<KafkaSettings>(builder.Configuration.GetSection(KafkaSettings.SectionName));
builder.Services.Configure<HangfireSettings>(builder.Configuration.GetSection(HangfireSettings.SectionName));
builder.Services.Configure<FileTransferSettings>(builder.Configuration.GetSection(FileTransferSettings.SectionName));
builder.Services.AddSingleton<KafkaClientFactory>();
builder.Services.AddSingleton<FileTransferKafkaService>();
builder.Services.AddScoped<FileTransferJob>();
builder.Services.AddScoped<FileTransferCleanupJob>();

if (!builder.Environment.IsEnvironment("Test"))
{
    var hangfireSettings = builder.Configuration.GetSection(HangfireSettings.SectionName).Get<HangfireSettings>() ?? new HangfireSettings();
    builder.Services.AddHangfire(config =>
        config.UsePostgreSqlStorage(
            options => options.UseNpgsqlConnection(hangfireSettings.ConnectionString),
            new PostgreSqlStorageOptions
            {
                SchemaName = hangfireSettings.Schema
            }));
    builder.Services.AddHangfireServer();
}

builder.Services.Configure<JwtSettings>(builder.Configuration.GetSection("Jwt"));
builder.Services.AddScoped<JwtTokenService>();

builder.Services.AddHttpClient();
builder.Services.AddControllers()
    .AddJsonOptions(options =>
    {
        options.JsonSerializerOptions.Converters.Add(new JsonStringEnumConverter());
    });

// Add Authentication configuration
builder.Services.AddAuthentication(options =>
    {
        options.DefaultAuthenticateScheme = JwtBearerDefaults.AuthenticationScheme;
        options.DefaultChallengeScheme = JwtBearerDefaults.AuthenticationScheme;
    })
    .AddJwtBearer(options =>
    {
        var jwtSection = builder.Configuration.GetSection("Jwt");
        var issuer = jwtSection["Issuer"];
        var audience = jwtSection["Audience"];
        var signingKey = jwtSection["SigningKey"];

        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidateAudience = true,
            ValidateIssuerSigningKey = true,
            ValidateLifetime = true,
            ValidIssuer = issuer,
            ValidAudience = audience,
            IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(signingKey ?? string.Empty)),
            ClockSkew = TimeSpan.FromMinutes(2)
        };
    })
    .AddGoogle(options =>
    {
        options.ClientId = builder.Configuration["Authentication:Google:ClientId"] ?? "default_client_id";
        options.ClientSecret = builder.Configuration["Authentication:Google:ClientSecret"] ?? "default_client_secret";
    })
    .AddMicrosoftAccount(microsoftOptions =>
    {
        microsoftOptions.ClientId = builder.Configuration["Authentication:Microsoft:ClientId"] ?? "default_client_id";
        microsoftOptions.ClientSecret = builder.Configuration["Authentication:Microsoft:ClientSecret"] ?? "default_client_secret";
    });

// Learn more about configuring OpenAPI at https://aka.ms/aspnet/openapi
builder.Services.AddOpenApi();
builder.Services.AddEndpointsApiExplorer();

var app = builder.Build();

// Apply EF Core migrations in development
if (app.Environment.IsDevelopment())
{
    using (var scope = app.Services.CreateScope())
    {
        var db = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
        // Simple retry logic for container startup race condition
        int retries = 5;
        while (retries > 0)
        {
            try
            {
                db.Database.Migrate();
                break;
            }
            catch (Exception)
            {
                retries--;
                if (retries == 0) throw;
                System.Threading.Thread.Sleep(2000);
            }
        }
    }
}

// Configure the HTTP request pipeline.
if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
    app.MapScalarApiReference();
    app.UseHangfireDashboard("/hangfire");
}

app.UseWebSockets();

app.UseAuthentication();
app.UseAuthorization();
app.UseMiddleware<TokenRefreshMiddleware>();

app.MapControllers();

app.Map("/ws/messaging", async context =>
{
    var handler = context.RequestServices.GetRequiredService<MessagingWebSocketHandler>();
    await handler.HandleAsync(context);
});

if (!app.Environment.IsEnvironment("Test"))
{
    RecurringJob.AddOrUpdate<FileTransferCleanupJob>(
        "cleanup-expired-transfers",
        job => job.CleanupExpiredAsync(),
        "*/5 * * * *");
}

app.Run();

/// <summary>
/// Application entry point type for integration testing.
/// </summary>
public partial class Program
{
}
