using System.Collections.Specialized;
using System.Linq;
using Avalonia.Controls;
using Avalonia.Threading;
using BinaryStars.ViewModels;

namespace BinaryStars.Views;

public partial class MainView : UserControl
{
    public MainView()
    {
        InitializeComponent();
        this.DataContextChanged += MainView_DataContextChanged;
    }

    private void MainView_DataContextChanged(object? sender, System.EventArgs e)
    {
        if (DataContext is MainViewModel vm)
        {
            vm.Messages.CollectionChanged += Messages_CollectionChanged;
        }
    }

    private void Messages_CollectionChanged(object? sender, NotifyCollectionChangedEventArgs e)
    {
        if (e.Action == NotifyCollectionChangedAction.Add)
        {
            Dispatcher.UIThread.Post(() =>
            {
                var lastItem = ((MainViewModel)DataContext!).Messages.LastOrDefault();
                if (lastItem != null)
                {
                    ChatList.ScrollIntoView(lastItem);
                }
            });
        }
    }
}
