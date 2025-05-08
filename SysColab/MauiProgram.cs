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
        #if ANDROID
            builder.Services.AddSingleton<IDeviceMetricService, SysColab.Platforms.Android.Services.DeviceMetricsService>();
        #elif WINDOWS
            builder.Services.AddSingleton<IDeviceMetricService, SysColab.Platforms.Windows.Services.DeviceMetricsService>();
        #endif
        builder.Services.AddScoped<FileService>();
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