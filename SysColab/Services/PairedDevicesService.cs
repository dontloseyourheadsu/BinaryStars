using SQLite;
using SysColab.Constants;
using SysColab.Models;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

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
    }
}
