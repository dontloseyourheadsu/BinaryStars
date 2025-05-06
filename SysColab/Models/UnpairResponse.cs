namespace SysColab.Models
{
    public class UnpairResponse
    {
        public bool Success { get; set; }
        public string DeviceId { get; set; }
        public string DeviceName { get; set; }
        public string ErrorMessage { get; set; }
    }
}