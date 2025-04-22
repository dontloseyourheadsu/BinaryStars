using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace SysColab.Models
{
    internal class DeviceMetrics
    {
        public int CpuUsage { get; set; }
        public int RamUsage { get; set; }
        public int StorageUsage { get; set; }
        public int NetworkUp { get; set; }
        public int NetworkDown { get; set; }
    }
}
