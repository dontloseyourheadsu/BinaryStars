using System;
using Avalonia;
using Microsoft.Extensions.DependencyInjection;
using BinaryStars.Services;
using BinaryStars.ViewModels;
using BinaryStars.Desktop.Services;

namespace BinaryStars.Desktop;

sealed class Program
{
    [STAThread]
    public static void Main(string[] args)
    {
        var services = new ServiceCollection();
        
        // Native Linux Implementation
        services.AddSingleton<IBluetoothChatService, LinuxBluetoothService>();
        services.AddSingleton<IHistoryService, HistoryService>();
        services.AddSingleton<IFilePickerService, AvaloniaFilePickerService>();
        services.AddSingleton<MainViewModel>();

        App.Services = services.BuildServiceProvider();

        BuildAvaloniaApp()
            .StartWithClassicDesktopLifetime(args);
    }

    public static AppBuilder BuildAvaloniaApp()
        => AppBuilder.Configure<App>()
            .UsePlatformDetect()
            .WithInterFont()
            .LogToTrace();
}
