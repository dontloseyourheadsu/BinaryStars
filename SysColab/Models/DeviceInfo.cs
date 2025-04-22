using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace SysColab.Models
{
    internal class DeviceInfo
    {
        public string Name { get; set; }
        public string Address { get; set; }
        public bool IsPaired { get; set; }

        public DeviceInfo(string name, string address, bool isPaired)
        {
            Name = name;
            Address = address;
            IsPaired = isPaired;
        }
    }
}
