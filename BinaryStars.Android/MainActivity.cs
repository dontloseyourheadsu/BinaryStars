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
    private const int BluetoothPermissionRequestCode = 1001;

    protected override void OnCreate(Bundle? savedInstanceState)
    {
        if (App.Services == null)
        {
            var services = new ServiceCollection();

            // Native Android Implementation
            services.AddSingleton<IBluetoothChatService, AndroidBluetoothService>();
            services.AddSingleton<IHistoryService, HistoryService>();
            services.AddSingleton<IFilePickerService, AvaloniaFilePickerService>();
            services.AddSingleton<MainViewModel>();

            App.Services = services.BuildServiceProvider();
        }

        base.OnCreate(savedInstanceState);
        RequestBluetoothPermissionsIfNeeded();
    }

    protected override AppBuilder CustomizeAppBuilder(AppBuilder builder)
    {
        return base.CustomizeAppBuilder(builder)
            .WithInterFont();
    }

    private void RequestBluetoothPermissionsIfNeeded()
    {
        var required = new List<string>();

        if (Build.VERSION.SdkInt >= BuildVersionCodes.S)
        {
            required.Add("android.permission.BLUETOOTH_SCAN");
            required.Add("android.permission.BLUETOOTH_CONNECT");
            required.Add("android.permission.BLUETOOTH_ADVERTISE");
        }

        required.Add(Manifest.Permission.AccessFineLocation);

        var pending = required
            .Distinct()
            .Where(permission => CheckSelfPermission(permission) != Permission.Granted)
            .ToArray();

        if (pending.Length > 0)
        {
            RequestPermissions(pending, BluetoothPermissionRequestCode);
        }
    }
}
