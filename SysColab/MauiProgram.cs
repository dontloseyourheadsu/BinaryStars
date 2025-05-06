using Microsoft.Extensions.Logging;
using SysColab.Constants;
using SysColab.Services;

namespace SysColab;

public static class MauiProgram
{
    public static MauiApp CreateMauiApp()
    {
        var builder = MauiApp.CreateBuilder();
        builder
            .UseMauiApp<App>()
            .ConfigureFonts(fonts => { fonts.AddFont("OpenSans-Regular.ttf", "OpenSansRegular"); });

        builder.Services.AddScoped<PairedDevicesService>();
        builder.Services.AddScoped<DeviceMappingService>();
        builder.Services.AddSingleton<ConnectivityService>();
        builder.Services.AddScoped<FileService>();
        builder.Services.AddScoped<RemoteInputService>();
        builder.Services.AddMauiBlazorWebView();

#if DEBUG
        builder.Services.AddBlazorWebViewDeveloperTools();
        builder.Logging.AddDebug();
#endif

        builder.Services.AddScoped(sp =>
            new HttpClient { BaseAddress = new Uri(ServerConstants.ServerBaseUrl) }
        );

        return builder.Build();
    }
}