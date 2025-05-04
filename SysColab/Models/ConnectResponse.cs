using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace SysColab.Models
{
    // Class to represent connect response messages
    public class ConnectResponse
    {
        public bool Success { get; set; }
        public string DeviceId { get; set; }
        public string DeviceName { get; set; }
        public string ErrorMessage { get; set; }
    }
}
