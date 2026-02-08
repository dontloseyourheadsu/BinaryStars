using BinaryStars.Application.Databases.DatabaseContexts;
using BinaryStars.Application.Databases.DatabaseModels.Accounts;
using BinaryStars.Application.Databases.Repositories.Accounts;
using BinaryStars.Application.Databases.Repositories.Devices;
using BinaryStars.Application.Databases.Repositories.Locations;
using BinaryStars.Application.Databases.Repositories.Notes;
using BinaryStars.Application.Databases.Repositories.Transfers;
using BinaryStars.Application.Services.Accounts;
using BinaryStars.Application.Services.Devices;
using BinaryStars.Application.Services.Locations;
using BinaryStars.Application.Services.Notes;
using BinaryStars.Application.Services.Transfers;
using BinaryStars.Application.Validators.Accounts;
using BinaryStars.Api.Services;
using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;

namespace BinaryStars.Api.Extensions;

/// <summary>
/// Service registration extensions for the API host.
/// </summary>
public static class ServicesExtensions
{
    /// <summary>
    /// Registers application-layer services, repositories, and validators.
    /// </summary>
    /// <param name="services">The service collection.</param>
    /// <returns>The updated service collection.</returns>
    public static IServiceCollection AddApplicationServices(this IServiceCollection services)
    {
        // Repositories
        services.AddScoped<IAccountRepository, AccountRepository>();
        services.AddScoped<IDeviceRepository, DeviceRepository>();
        services.AddScoped<INotesRepository, NotesRepository>();
        services.AddScoped<IFileTransferRepository, FileTransferRepository>();
        services.AddScoped<ILocationHistoryRepository, LocationHistoryRepository>();

        // Services
        services.AddScoped<IAccountsWriteService, AccountsWriteService>();
        services.AddScoped<IAccountsReadService, AccountsReadService>();
        services.AddScoped<IDevicesReadService, DevicesReadService>();
        services.AddScoped<IDevicesWriteService, DevicesWriteService>();
        services.AddScoped<INotesReadService, NotesReadService>();
        services.AddScoped<INotesWriteService, NotesWriteService>();
        services.AddScoped<IFileTransfersReadService, FileTransfersService>();
        services.AddScoped<IFileTransfersWriteService, FileTransfersService>();
        services.AddScoped<ILocationHistoryReadService, LocationHistoryService>();
        services.AddScoped<ILocationHistoryWriteService, LocationHistoryService>();
        services.AddScoped<ExternalIdentityValidator>();
        services.AddSingleton<MessagingConnectionManager>();
        services.AddScoped<MessagingKafkaService>();
        services.AddScoped<MessagingWebSocketHandler>();

        // Validators
        services.AddScoped<AuthValidator>();

        return services;
    }

    /// <summary>
    /// Registers database services and ASP.NET Core Identity.
    /// </summary>
    /// <param name="services">The service collection.</param>
    /// <param name="configuration">The application configuration.</param>
    /// <returns>The updated service collection.</returns>
    public static IServiceCollection AddDatabaseServices(this IServiceCollection services, IConfiguration configuration)
    {
        services.AddDbContext<ApplicationDbContext>(options =>
            options.UseNpgsql(configuration.GetConnectionString("DefaultConnection")));

        services.AddIdentity<UserDbModel, IdentityRole<Guid>>(options =>
            {
                options.User.RequireUniqueEmail = true;
                options.Password.RequireDigit = true;
                options.Password.RequiredLength = 6;
            })
            .AddEntityFrameworkStores<ApplicationDbContext>()
            .AddDefaultTokenProviders();

        return services;
    }
}
