using SQLite;

namespace SysColab.Models
{
    public class DeviceMapping
    {
        [PrimaryKey]
        public string MacAddress { get; set; }

        public string Id { get; set; }
    }
}
