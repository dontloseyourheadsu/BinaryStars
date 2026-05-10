using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Platform.Storage;

namespace BinaryStars.Services;

public class AvaloniaFilePickerService : IFilePickerService
{
    public async Task<IEnumerable<(string Name, byte[] Data)>> PickFilesAsync()
    {
        var topLevel = GetTopLevel();
        if (topLevel == null) return Enumerable.Empty<(string Name, byte[] Data)>();

        var files = await topLevel.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
        {
            Title = "Select Files to Send",
            AllowMultiple = true
        });

        if (files == null || !files.Any()) return Enumerable.Empty<(string Name, byte[] Data)>();

        var results = new List<(string Name, byte[] Data)>();
        foreach (var file in files)
        {
            using var stream = await file.OpenReadAsync();
            using var memoryStream = new MemoryStream();
            await stream.CopyToAsync(memoryStream);
            results.Add((file.Name, memoryStream.ToArray()));
        }

        return results;
    }

    private TopLevel? GetTopLevel()
    {
        if (Application.Current?.ApplicationLifetime is IClassicDesktopStyleApplicationLifetime desktop)
        {
            return desktop.MainWindow;
        }
        
        if (Application.Current?.ApplicationLifetime is ISingleViewApplicationLifetime singleView)
        {
            return TopLevel.GetTopLevel(singleView.MainView);
        }

        return null;
    }
}
