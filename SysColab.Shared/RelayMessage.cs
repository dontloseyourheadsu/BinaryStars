using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace SysColab.Shared
{
    public record RelayMessage(string TargetId, string SerializedJson);
}
