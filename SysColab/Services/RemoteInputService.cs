using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace SysColab.Services
{
    public class RemoteInputService
    {
        /// <summary>
        /// Sends keyboard input to the specified device address.
        /// </summary>
        public Task SendKeyboardInputAsync(string deviceAddress, string input)
        {
            // Mock delay to simulate transport
            Console.WriteLine($"[RemoteInputService] Sending to {deviceAddress}: {input}");
            return Task.CompletedTask;
        }
    }
}
