using System;
using System.Collections.Generic;
using System.IO;
using System.Threading.Tasks;
using SQLite;

namespace BinaryStars.Services;

public class SettingEntity
{
    [PrimaryKey]
    public string Key { get; set; } = "";
    public string Value { get; set; } = "";
}

public class ChatEntity
{
    [PrimaryKey]
    public string Address { get; set; } = "";
    public string DeviceId { get; set; } = "";
    public string DeviceName { get; set; } = "";
    public DateTime LastSeen { get; set; }
}

public class MessageEntity
{
    [PrimaryKey, AutoIncrement]
    public int Id { get; set; }
    [Indexed]
    public string ChatAddress { get; set; } = "";
    public string Sender { get; set; } = "";
    public string Text { get; set; } = "";
    public DateTimeOffset Timestamp { get; set; }
    public bool IsMe { get; set; }
    
    public string? AttachmentName { get; set; }
    public long? AttachmentSize { get; set; }
    public string? AttachmentType { get; set; }
    public string? AttachmentLocalPath { get; set; }
    public byte[]? AttachmentData { get; set; }
}

public interface IDatabaseService
{
    Task InitializeAsync();
    Task<string> GetDeviceIdAsync();
    Task<string> GetDeviceNameAsync();
    Task SetDeviceNameAsync(string name);
    Task<List<ChatEntity>> GetChatsAsync();
    Task SaveChatAsync(ChatEntity chat);
    Task<List<MessageEntity>> GetMessagesForChatAsync(string address);
    Task SaveMessageAsync(MessageEntity msg);
}

public class DatabaseService : IDatabaseService
{
    private SQLiteAsyncConnection? _db;

    public async Task InitializeAsync()
    {
        if (_db != null) return;

        var folder = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        var dir = Path.Combine(folder, "BinaryStars");
        if (!Directory.Exists(dir))
        {
            Directory.CreateDirectory(dir);
        }
        var dbPath = Path.Combine(dir, "binarystars.db");

        _db = new SQLiteAsyncConnection(dbPath);
        await _db.CreateTableAsync<SettingEntity>();
        await _db.CreateTableAsync<ChatEntity>();
        await _db.CreateTableAsync<MessageEntity>();

        // Ensure Device ID exists (UUID v7)
        var deviceId = await GetDeviceIdInternalAsync();
        if (string.IsNullOrEmpty(deviceId))
        {
            // Guid v7
            var newId = Guid.CreateVersion7().ToString();
            await _db.InsertOrReplaceAsync(new SettingEntity { Key = "DeviceId", Value = newId });
        }

        // Ensure Device Name exists
        var deviceName = await GetDeviceNameInternalAsync();
        if (string.IsNullOrEmpty(deviceName))
        {
            var defaultName = $"{Environment.MachineName} ({Environment.OSVersion.Platform})";
            await _db.InsertOrReplaceAsync(new SettingEntity { Key = "DeviceName", Value = defaultName });
        }
    }

    private async Task<string> GetDeviceIdInternalAsync()
    {
        var setting = await _db!.Table<SettingEntity>().Where(s => s.Key == "DeviceId").FirstOrDefaultAsync();
        return setting?.Value ?? "";
    }

    private async Task<string> GetDeviceNameInternalAsync()
    {
        var setting = await _db!.Table<SettingEntity>().Where(s => s.Key == "DeviceName").FirstOrDefaultAsync();
        return setting?.Value ?? "";
    }

    public async Task<string> GetDeviceIdAsync()
    {
        await InitializeAsync();
        return await GetDeviceIdInternalAsync();
    }

    public async Task<string> GetDeviceNameAsync()
    {
        await InitializeAsync();
        return await GetDeviceNameInternalAsync();
    }

    public async Task SetDeviceNameAsync(string name)
    {
        await InitializeAsync();
        await _db!.InsertOrReplaceAsync(new SettingEntity { Key = "DeviceName", Value = name });
    }

    public async Task<List<ChatEntity>> GetChatsAsync()
    {
        await InitializeAsync();
        return await _db!.Table<ChatEntity>().OrderByDescending(c => c.LastSeen).ToListAsync();
    }

    public async Task SaveChatAsync(ChatEntity chat)
    {
        await InitializeAsync();
        await _db!.InsertOrReplaceAsync(chat);
    }

    public async Task<List<MessageEntity>> GetMessagesForChatAsync(string address)
    {
        await InitializeAsync();
        return await _db!.Table<MessageEntity>()
            .Where(m => m.ChatAddress == address)
            .OrderBy(m => m.Timestamp)
            .ToListAsync();
    }

    public async Task SaveMessageAsync(MessageEntity msg)
    {
        await InitializeAsync();
        await _db!.InsertAsync(msg);
    }
}
