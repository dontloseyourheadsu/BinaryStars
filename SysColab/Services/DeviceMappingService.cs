using SQLite;
using SysColab.Constants;
using SysColab.Models;

namespace SysColab.Services
{
    public class DeviceMappingService
    {
        private readonly SQLiteAsyncConnection _database;

        public DeviceMappingService()
        {
            _database = new SQLiteAsyncConnection(DatabaseConstants.DatabasePath, DatabaseConstants.Flags);
            _database.CreateTableAsync<DeviceMapping>().Wait();
        }

        public async Task<List<DeviceMapping>> GetAllMappingsAsync()
        {
            return await _database.Table<DeviceMapping>().ToListAsync();
        }

        public async Task<DeviceMapping> GetMappingByMacAsync(string macAddress)
        {
            return await _database.Table<DeviceMapping>()
                            .Where(i => i.MacAddress == macAddress)
                            .FirstOrDefaultAsync();
        }

        public async Task<Guid> GetIdByMacAsync(string macAddress)
        {
            var deviceMapping = await _database.Table<DeviceMapping>()
                            .Where(i => i.MacAddress == macAddress)
                            .FirstOrDefaultAsync();
            if (deviceMapping is not null && !string.IsNullOrWhiteSpace(deviceMapping.Id))
            {
                return Guid.Parse(deviceMapping.Id);
            }
            else
            {
                var newGuid = Guid.NewGuid();
                var newMapping = new DeviceMapping
                {
                    MacAddress = macAddress,
                    Id = newGuid.ToString()
                };

                await _database.InsertOrReplaceAsync(newMapping);
                
                return newGuid;
            }
        }

        public async Task<int> SaveMappingAsync(DeviceMapping mapping)
        {
            return await _database.InsertOrReplaceAsync(mapping);
        }

        public async Task<int> DeleteMappingAsync(DeviceMapping mapping)
        {
            return await _database.DeleteAsync(mapping);
        }
    }
}
