using BinaryStars.Api.Extensions;
using BinaryStars.Application.Databases.DatabaseContexts;
using Serilog;
using Serilog.Sinks.Grafana.Loki;
using Scalar.AspNetCore;

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

builder.Services.AddControllers();

// Add Authentication configuration
builder.Services.AddAuthentication()
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

// Ensure DB created
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
                db.Database.EnsureCreated();
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
}

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.Run();
