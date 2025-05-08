using System.Net.NetworkInformation;

namespace SysColab.Helpers
{
    /// <summary>
    /// Helper class for device-related functionalities.
    /// </summary>
    internal static class DeviceHelpers
    {
        /// <summary>
        /// Get the device's MAC address.
        /// </summary>
        /// <returns>Returns the MAC address as a string.</returns>
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

        /// <summary>
        /// Get the device's IP address.
        /// </summary>
        /// <returns>Returns the IP address as a string.</returns>
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
