using BinaryStars.Application.Databases.DatabaseContexts;
using BinaryStars.Application.Databases.DatabaseModels.Accounts;
using BinaryStars.Application.Databases.Repositories.Accounts;
using BinaryStars.Application.Databases.Repositories.Devices;
using BinaryStars.Application.Services.Accounts;
using BinaryStars.Application.Services.Devices;
using BinaryStars.Application.Validators.Accounts;
using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;

namespace BinaryStars.Api.Extensions;

public static class ServicesExtensions
{
    public static IServiceCollection AddApplicationServices(this IServiceCollection services)
    {
        // Repositories
        services.AddScoped<IAccountRepository, AccountRepository>();
        services.AddScoped<IDeviceRepository, DeviceRepository>();

        // Services
        services.AddScoped<IAccountsWriteService, AccountsWriteService>();
        services.AddScoped<IAccountsReadService, AccountsReadService>();
        services.AddScoped<IDevicesReadService, DevicesReadService>();

        // Validators
        services.AddScoped<AuthValidator>();

        return services;
    }

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
