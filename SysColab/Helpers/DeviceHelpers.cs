using System;
using System.Collections.Generic;
using System.Linq;
using System.Net.NetworkInformation;
using System.Text;
using System.Threading.Tasks;

namespace SysColab.Helpers
{
    internal static class DeviceHelpers
    {
        public static string GetMacAddress()
        {
            var macAddress = NetworkInterface.GetAllNetworkInterfaces()
                .Where(nic => nic.OperationalStatus == OperationalStatus.Up &&
                              nic.NetworkInterfaceType != NetworkInterfaceType.Loopback)
                .Select(nic => nic.GetPhysicalAddress().ToString())
                .FirstOrDefault();

            return macAddress ?? "No se encontró la dirección MAC";
        }

        public static string GetDeviceName()
        {
            var deviceName = Environment.MachineName;
            return deviceName ?? "Unknown";
        }
    }
}
