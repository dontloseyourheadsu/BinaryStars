using SQLite;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace SysColab.Models
{
    public class DeviceMapping
    {
        [PrimaryKey]
        public string MacAddress { get; set; }

        public string Id { get; set; }
    }
}
