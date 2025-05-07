using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace SysColab.Shared
{
    public record FileOffer
    (
        Guid FileId,
        string Name,
        long Size,
        Guid SenderId
    );

}
