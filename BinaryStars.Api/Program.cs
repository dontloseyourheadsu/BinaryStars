using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.Identity.Web;
using Microsoft.Identity.Abstractions;
using Microsoft.Identity.Web.Resource;
using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;
using BinaryStars.Api.Data;
using Serilog;
using Serilog.Sinks.Grafana.Loki;
using ServiceStack;
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
builder.Services.AddDbContext<ApplicationDbContext>(options =>
    options.UseNpgsql(builder.Configuration.GetConnectionString("DefaultConnection")));

builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddMicrosoftIdentityWebApi(builder.Configuration.GetSection("AzureAd"));

builder.Services.AddAuthorization();

builder.Services.AddIdentityApiEndpoints<IdentityUser>()
    .AddEntityFrameworkStores<ApplicationDbContext>();

builder.Services.AddAuthentication()
    .AddGoogle(options =>
    {
        options.ClientId = builder.Configuration["Authentication:Google:ClientId"]!;
        options.ClientSecret = builder.Configuration["Authentication:Google:ClientSecret"]!;
    });

// Learn more about configuring OpenAPI at https://aka.ms/aspnet/openapi
builder.Services.AddOpenApi();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddServiceStackOpenApi();

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

// app.UseHttpsRedirection();

// Map Identity endpoints
app.MapIdentityApi<IdentityUser>();

app.Run();
