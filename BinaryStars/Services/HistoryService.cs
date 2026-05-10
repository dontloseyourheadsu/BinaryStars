using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using System.IO;
using SQLite;
using BinaryStars.Models;

namespace BinaryStars.Services;

public interface IHistoryService
{
    Task SaveMessage(ChatMessage message);
    Task<IEnumerable<ChatMessage>> GetMessages();
    Task ClearHistory();
}

public class HistoryService : IHistoryService
{
    private SQLiteAsyncConnection? _db;

    private async Task Init()
    {
        if (_db != null) return;
        
        var databasePath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "history.db3");
        _db = new SQLiteAsyncConnection(databasePath);
        await _db.CreateTableAsync<ChatMessageEntity>();
    }

    public async Task SaveMessage(ChatMessage message)
    {
        await Init();
        var entity = new ChatMessageEntity
        {
            Sender = message.Sender,
            Text = message.Text,
            Timestamp = message.Timestamp.DateTime,
            IsMe = message.IsMe,
            AttachmentName = message.Attachment?.Name,
            AttachmentSize = message.Attachment?.Size ?? 0,
            IsImage = message.Attachment?.IsImage ?? false,
            AttachmentData = message.Attachment?.Data
        };
        await _db!.InsertAsync(entity);
    }

    public async Task<IEnumerable<ChatMessage>> GetMessages()
    {
        await Init();
        var entities = await _db!.Table<ChatMessageEntity>().OrderBy(x => x.Timestamp).ToListAsync();
        return entities.Select(e => new ChatMessage(
            e.Sender, 
            e.Text, 
            new DateTimeOffset(e.Timestamp), 
            e.IsMe, 
            e.AttachmentName != null ? new FileAttachment(e.AttachmentName, e.AttachmentSize, "application/octet-stream", IsImage: e.IsImage, Data: e.AttachmentData) : null));
    }

    public async Task ClearHistory()
    {
        await Init();
        await _db!.DeleteAllAsync<ChatMessageEntity>();
    }
}

public class ChatMessageEntity
{
    [PrimaryKey, AutoIncrement]
    public int Id { get; set; }
    public string Sender { get; set; } = string.Empty;
    public string Text { get; set; } = string.Empty;
    public DateTime Timestamp { get; set; }
    public bool IsMe { get; set; }
    public string? AttachmentName { get; set; }
    public long AttachmentSize { get; set; }
    public bool IsImage { get; set; }
    public byte[]? AttachmentData { get; set; }
}
