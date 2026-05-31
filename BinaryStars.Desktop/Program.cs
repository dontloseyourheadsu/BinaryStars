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
        services.AddSingleton<IDatabaseService, DatabaseService>();
        services.AddSingleton<IBluetoothService, LinuxBluetoothService>();
        services.AddSingleton<BluetoothChatService>();
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
