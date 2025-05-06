using Android.App;
using Android.OS;
using Android.Net;
using SysColab.Services;
using Android.Content;

// Alias directives to resolve ambiguity
using SystemFile = System.IO.File;
using AndroidEnvironment = Android.OS.Environment;
using AndroidApp = Android.App.Application;

namespace SysColab.Platforms.Android.Services
{
    public class DeviceMetricsService : IDeviceMetricService
    {
        public double GetCpuUsage()
        {
            try
            {
                var cpuInfo1 = SystemFile.ReadAllLines("/proc/stat")[0];
                Thread.Sleep(500);
                var cpuInfo2 = SystemFile.ReadAllLines("/proc/stat")[0];

                var values1 = cpuInfo1.Split(' ', StringSplitOptions.RemoveEmptyEntries).Skip(1).Select(double.Parse).ToArray();
                var values2 = cpuInfo2.Split(' ', StringSplitOptions.RemoveEmptyEntries).Skip(1).Select(double.Parse).ToArray();

                var idle1 = values1[3];
                var idle2 = values2[3];

                var total1 = values1.Sum();
                var total2 = values2.Sum();

                var idleDelta = idle2 - idle1;
                var totalDelta = total2 - total1;

                return (1.0 - (idleDelta / totalDelta)) * 100.0;
            }
            catch
            {
                return 0.0;
            }
        }

        public double GetRamUsage()
        {
            var activityManager = (ActivityManager)AndroidApp.Context.GetSystemService(Context.ActivityService);
            var memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.GetMemoryInfo(memoryInfo);

            double totalRam = memoryInfo.TotalMem;
            double availRam = memoryInfo.AvailMem;
            return ((totalRam - availRam) / totalRam) * 100.0;
        }

        public double GetStorageUsage()
        {
            var statFs = new StatFs(AndroidEnvironment.DataDirectory.AbsolutePath);
            long total = statFs.BlockCountLong * statFs.BlockSizeLong;
            long free = statFs.AvailableBlocksLong * statFs.BlockSizeLong;
            return ((double)(total - free) / total) * 100.0;
        }

        public double GetNetworkUploadSpeed()
        {
            try
            {
                long txBytes1 = TrafficStats.TotalTxBytes;
                Thread.Sleep(1000);
                long txBytes2 = TrafficStats.TotalTxBytes;

                return (txBytes2 - txBytes1) / 1024.0; // KB/s
            }
            catch
            {
                return 0.0;
            }
        }

        public double GetNetworkDownloadSpeed()
        {
            try
            {
                long rxBytes1 = TrafficStats.TotalRxBytes;
                Thread.Sleep(1000);
                long rxBytes2 = TrafficStats.TotalRxBytes;

                return (rxBytes2 - rxBytes1) / 1024.0; // KB/s
            }
            catch
            {
                return 0.0;
            }
        }
    }
}
