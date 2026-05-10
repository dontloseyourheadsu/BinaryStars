using System.Collections.Generic;
using System.Threading.Tasks;

namespace BinaryStars.Services;

public interface IFilePickerService
{
    Task<IEnumerable<(string Name, byte[] Data)>> PickFilesAsync();
}
