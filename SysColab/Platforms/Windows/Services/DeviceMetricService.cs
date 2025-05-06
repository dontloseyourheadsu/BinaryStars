using System.Net.NetworkInformation;
using System.Diagnostics;

using SysColab.Services;

namespace SysColab.Platforms.Windows.Services
{
    public class DeviceMetricsService : IDeviceMetricService
    {
        public double GetCpuUsage()
        {
            try
            {
                using var cpuCounter = new PerformanceCounter("Processor", "% Processor Time", "_Total");
                cpuCounter.NextValue();
                Thread.Sleep(500);
                return Math.Round(cpuCounter.NextValue(), 2);
            }
            catch
            {
                return 0.0;
            }
        }

        public double GetRamUsage()
        {
            try
            {
                var pc = new PerformanceCounter("Memory", "Available MBytes");
                double available = pc.NextValue();
                double total = GetTotalMemoryInMBytes();
                return ((total - available) / total) * 100.0;
            }
            catch
            {
                return 0.0;
            }
        }

        private double GetTotalMemoryInMBytes()
        {
            double totalMemory = 0;
            var searcher = new System.Management.ManagementObjectSearcher("SELECT TotalPhysicalMemory FROM Win32_ComputerSystem");
            foreach (var obj in searcher.Get())
            {
                totalMemory = Convert.ToDouble(obj["TotalPhysicalMemory"]);
            }
            return totalMemory / (1024 * 1024);
        }


        public double GetStorageUsage()
        {
            try
            {
                var drive = DriveInfo.GetDrives().FirstOrDefault(d => d.IsReady && d.Name == "C:\\");
                if (drive != null)
                {
                    double total = drive.TotalSize;
                    double free = drive.TotalFreeSpace;
                    return ((total - free) / total) * 100.0;
                }
                return 0.0;
            }
            catch
            {
                return 0.0;
            }
        }

        public double GetNetworkUploadSpeed()
        {
            try
            {
                var interfaces = NetworkInterface.GetAllNetworkInterfaces()
                    .Where(ni => ni.OperationalStatus == OperationalStatus.Up && ni.NetworkInterfaceType != NetworkInterfaceType.Loopback);

                long bytesSent1 = interfaces.Sum(ni => ni.GetIPv4Statistics().BytesSent);
                Thread.Sleep(1000);
                long bytesSent2 = interfaces.Sum(ni => ni.GetIPv4Statistics().BytesSent);

                double uploadSpeed = (bytesSent2 - bytesSent1) / 1024.0; // KB/s
                return uploadSpeed;
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
                var interfaces = NetworkInterface.GetAllNetworkInterfaces()
                    .Where(ni => ni.OperationalStatus == OperationalStatus.Up && ni.NetworkInterfaceType != NetworkInterfaceType.Loopback);

                long bytesReceived1 = interfaces.Sum(ni => ni.GetIPv4Statistics().BytesReceived);
                Thread.Sleep(1000);
                long bytesReceived2 = interfaces.Sum(ni => ni.GetIPv4Statistics().BytesReceived);

                double downloadSpeed = (bytesReceived2 - bytesReceived1) / 1024.0; // KB/s
                return downloadSpeed;
            }
            catch
            {
                return 0.0;
            }
        }
    }
}
