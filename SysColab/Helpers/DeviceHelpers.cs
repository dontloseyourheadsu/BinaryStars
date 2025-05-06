using System.Net.NetworkInformation;

namespace SysColab.Helpers
{
    internal static class DeviceHelpers
    {
        public static string GetMacAddress()
        {
            var nic = NetworkInterface
                .GetAllNetworkInterfaces()
                .FirstOrDefault(n =>
                    n.OperationalStatus == OperationalStatus.Up &&
                    n.NetworkInterfaceType != NetworkInterfaceType.Loopback
                );

            return nic?.GetPhysicalAddress()?.ToString() ?? string.Empty;
        }

        public static string GetDeviceName()
        {
            try
            {
                return DeviceInfo.Current.Name ?? "Unknown";
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error getting device name: {ex.Message}");
                return "Unknown";
            }
        }
    }
}
