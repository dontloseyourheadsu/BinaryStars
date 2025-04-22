using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace SysColab.Models
{
    internal class FileItem
    {
        public string Name { get; set; }
        public long Size { get; set; }
        public int Progress { get; set; }
    }
}
