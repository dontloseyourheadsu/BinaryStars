using System;
using System.IO;
using Avalonia.Controls;
using Avalonia.Interactivity;
using Avalonia.Platform.Storage;
using BinaryStars.ViewModels;

namespace BinaryStars.Views;

public partial class MainView : UserControl
{
    public MainView()
    {
        InitializeComponent();
    }

    private async void OnAddAttachmentClick(object sender, RoutedEventArgs e)
    {
        var topLevel = TopLevel.GetTopLevel(this);
        if (topLevel == null) return;

        var files = await topLevel.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
        {
            Title = "Select File/Image",
            AllowMultiple = false
        });

        if (files != null && files.Count > 0)
        {
            var file = files[0];
            try
            {
                using var stream = await file.OpenReadAsync();
                using var memoryStream = new MemoryStream();
                await stream.CopyToAsync(memoryStream);
                var bytes = memoryStream.ToArray();

                if (DataContext is MainViewModel vm)
                {
                    await vm.SendFileAttachmentAsync(file.Name, bytes);
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Failed to read attachment file: {ex.Message}");
            }
        }
    }
}
