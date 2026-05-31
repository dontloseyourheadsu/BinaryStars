using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using Tmds.DBus;

namespace BinaryStars.Desktop.Helpers;

[DBusInterface("org.freedesktop.DBus.ObjectManager")]
public interface IObjectManager : IDBusObject
{
    Task<IDictionary<ObjectPath, IDictionary<string, IDictionary<string, object>>>> GetManagedObjectsAsync();
}

public static class BlueZHelper
{
    public static async Task<int> FindRfcommChannelAsync(string address, string uuid)
    {
        try
        {
            var connection = Connection.System;
            var objectManager = connection.CreateProxy<IObjectManager>("org.bluez", "/");
            var objects = await objectManager.GetManagedObjectsAsync();

            var normalizedAddr = address.ToUpperInvariant();

            foreach (var obj in objects)
            {
                var path = obj.Key.ToString();
                // Look for nodes under the device path that might contain service info
                if (path.Contains(normalizedAddr.Replace(":", "_")))
                {
                    // In BlueZ, actual SPP channels are often not exposed as properties 
                    // unless a profile is active. 
                }
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[BlueZHelper] Error: {ex.Message}");
        }
        return -1;
    }
}
