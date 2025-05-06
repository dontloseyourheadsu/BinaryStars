namespace SysColab.Services
{
    public interface IDeviceMetricService
    {
        double GetCpuUsage();
        double GetRamUsage();
        double GetStorageUsage();
        double GetNetworkUploadSpeed();
        double GetNetworkDownloadSpeed();
    }
}
