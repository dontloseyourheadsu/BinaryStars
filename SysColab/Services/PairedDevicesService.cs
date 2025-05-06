using SQLite;
using SysColab.Constants;
using SysColab.Models;
namespace SysColab.Services
{
    public class PairedDevicesService
    {
        private readonly SQLiteAsyncConnection _database;
        public PairedDevicesService()
        {
            _database = new SQLiteAsyncConnection(DatabaseConstants.DatabasePath, DatabaseConstants.Flags);
            _database.CreateTableAsync<PairedDevices>().Wait();
        }
        public async Task<List<PairedDevices>> GetAllPairedDevicesAsync()
        {
            return await _database.Table<PairedDevices>().ToListAsync();
        }
        public async Task<List<PairedDevices>> GetPairedDevicesForId(string id)
        {
            return await _database.Table<PairedDevices>()
                            .Where(i => i.IdA == id || i.IdB == id)
                            .ToListAsync();
        }
        public async Task<bool> SavePairedDevicesAsync(string idA, string idB)
        {
            var pairedDevices = new PairedDevices
            {
                IdA = idA,
                IdB = idB
            };
            var modifiedRows = await _database.InsertOrReplaceAsync(pairedDevices);
            return modifiedRows > 0;
        }

        public async Task<bool> RemovePairedDevicesAsync(string idA, string idB)
        {
            // Use ExecuteAsync with a DELETE query instead of DeleteAsync
            var query = "DELETE FROM PairedDevices WHERE (IdA = ? AND IdB = ?) OR (IdA = ? AND IdB = ?)";
            var result = await _database.ExecuteAsync(query, idA, idB, idB, idA);

            return result > 0;
        }
    }
}