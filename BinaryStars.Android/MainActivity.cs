using Android.App;
using Android.Content;
using Android.Content.PM;
using Android.OS;
using Android.Views;
using Android;
using System.Collections.Generic;
using System.Linq;
using Avalonia;
using Avalonia.Android;
using Microsoft.Extensions.DependencyInjection;
using BinaryStars.Services;
using BinaryStars.ViewModels;
using BinaryStars.Android.Services;
using AndroidX.Core.App;
using AndroidX.Core.Content;

namespace BinaryStars.Android;

[Activity(
    Label = "BinaryStars",
    Theme = "@style/MyTheme.NoActionBar",
    Icon = "@drawable/icon",
    MainLauncher = true,
    ConfigurationChanges = ConfigChanges.Orientation | ConfigChanges.ScreenSize | ConfigChanges.UiMode,
    WindowSoftInputMode = SoftInput.AdjustPan,
    LaunchMode = LaunchMode.SingleTask)]
public class MainActivity : AvaloniaMainActivity<App>
{
    protected override void OnCreate(Bundle? savedInstanceState)
    {
        if (App.Services == null)
        {
            var services = new ServiceCollection();
            services.AddSingleton<IDatabaseService, DatabaseService>();
            services.AddSingleton<IBluetoothService, AndroidBluetoothService>();
            services.AddSingleton<BluetoothChatService>();
            services.AddSingleton<MainViewModel>();

            App.Services = services.BuildServiceProvider();
        }

        InTheHand.AndroidActivity.CurrentActivity = this;
        base.OnCreate(savedInstanceState);
        _ = PermissionHelper.EnsureBluetoothPermissions(this);
    }

    protected override AppBuilder CustomizeAppBuilder(AppBuilder builder)
    {
        return base.CustomizeAppBuilder(builder)
            .WithInterFont();
    }

    public override void OnRequestPermissionsResult(int requestCode, string[] permissions, Permission[] grantResults)
    {
        PermissionHelper.OnRequestPermissionsResult(requestCode, permissions, grantResults);
        base.OnRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
