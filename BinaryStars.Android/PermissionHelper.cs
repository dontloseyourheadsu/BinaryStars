using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using Android;
using Android.Content.PM;
using AndroidX.Core.App;
using AndroidX.Core.Content;
using Avalonia.Android;

namespace BinaryStars.Android;

public static class PermissionHelper
{
    private static TaskCompletionSource<bool>? _tcs;
    private const int RequestCode = 1001;

    public static async Task<bool> EnsureBluetoothPermissions(AvaloniaMainActivity activity)
    {
        var permissions = new List<string>();

        if (global::Android.OS.Build.VERSION.SdkInt >= global::Android.OS.BuildVersionCodes.S)
        {
            permissions.Add(Manifest.Permission.BluetoothScan);
            permissions.Add(Manifest.Permission.BluetoothConnect);
            permissions.Add(Manifest.Permission.BluetoothAdvertise);
            permissions.Add(Manifest.Permission.AccessFineLocation);
        }
        else
        {
            permissions.Add(Manifest.Permission.Bluetooth);
            permissions.Add(Manifest.Permission.BluetoothAdmin);
            permissions.Add(Manifest.Permission.AccessFineLocation);
        }

        var needed = new List<string>();
        foreach (var p in permissions)
        {
            if (ContextCompat.CheckSelfPermission(activity, p) != Permission.Granted)
            {
                needed.Add(p);
            }
        }

        if (needed.Count == 0) return true;

        _tcs = new TaskCompletionSource<bool>();
        ActivityCompat.RequestPermissions(activity, needed.ToArray(), RequestCode);
        
        return await _tcs.Task;
    }

    public static void OnRequestPermissionsResult(int requestCode, string[] permissions, Permission[] grantResults)
    {
        if (requestCode == RequestCode)
        {
            bool allGranted = true;
            foreach (var res in grantResults)
            {
                if (res != Permission.Granted)
                {
                    allGranted = false;
                    break;
                }
            }
            _tcs?.TrySetResult(allGranted);
        }
    }
}
