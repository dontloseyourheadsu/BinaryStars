using Microsoft.AspNetCore.Components.Forms;
using System.Net.Http.Json;
using System.Text.Json;

namespace SysColab.Services
{
    public class FileService
    {
        private const long FiveMb = 5L * 1024 * 1024; // 5 MB
        private readonly HttpClient _http;

        public FileService(HttpClient http) => _http = http;

        /// <summary>
        /// Uploads a file (max 5 MB) to the relay server and triggers a WS push.
        /// </summary>
        /// <param name="file">Browser file selected in Blazor.</param>
        /// <param name="senderId">Current device UUID.</param>
        /// <param name="targetId">Target device UUID.</param>
        /// <returns>fileId returned by the server or null on failure.</returns>
        public async Task<Guid?> UploadAsync(IBrowserFile file, Guid senderId, Guid targetId)
        {
            if (file.Size > FiveMb) return null; // Guard early

            using var content = new MultipartFormDataContent();

            await using var stream = file.OpenReadStream(file.Size);
            content.Add(new StreamContent(stream), "file", file.Name);
            content.Add(new StringContent(senderId.ToString()), "senderId");
            content.Add(new StringContent(targetId.ToString()), "targetId");

            var resp = await _http.PostAsync("/api/file", content);
            if (!resp.IsSuccessStatusCode) return null;

            var json = await resp.Content.ReadFromJsonAsync<JsonElement>();
            return json.TryGetProperty("fileId", out var prop) ? prop.GetGuid() : null;
        }

        /// <summary>
        /// Downloads a file blob by id. The server auto‑removes the blob after first fetch.
        /// </summary>
        public async Task<byte[]?> DownloadAsync(Guid fileId)
        {
            try
            {
                return await _http.GetByteArrayAsync($"/api/file/{fileId}");
            }
            catch
            {
                return null;
            }
        }
    }
}
