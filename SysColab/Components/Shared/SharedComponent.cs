using System.Net.Http.Json;
using System.Text.Json;
using Microsoft.AspNetCore.Components;
using SysColab.Constants;
using SysColab.Helpers;
using SysColab.Models;
using SysColab.Services;
using DeviceInfo = SysColab.Shared.DeviceInfo;

namespace SysColab.Components.Shared
{
    public class SharedComponent : ComponentBase, IDisposable
    {
        [Inject] protected NavigationManager Navigation { get; set; }
        [Inject] protected ConnectivityService ConnectivityService { get; set; }
        [Inject] protected PairedDevicesService PairedDevicesService { get; set; }
        [Inject] protected DeviceMappingService DeviceMappingService { get; set; }
        [Inject] protected HttpClient HttpClient { get; set; }
        [Inject] IDeviceMetricService DeviceMetricService { get; set; }

        protected List<DeviceInfo> Devices = new();
        protected DeviceMetrics Metrics = new();
        protected List<DeviceInfo> OnlinePairedDevices = new();
        protected DeviceInfo CurrentDeviceInfo;
        protected string StatusMessage = "";
        protected bool IsError = false;
        protected Guid CurrentDeviceId;
        protected CancellationTokenSource? _cts;

        protected override async Task OnInitializedAsync()
        {
            try
            {
                // Initialize the device info
                var deviceMacAddress = DeviceHelpers.GetMacAddress();
                CurrentDeviceId = await DeviceMappingService.GetIdByMacAsync(deviceMacAddress);
                var deviceName = DeviceHelpers.GetDeviceName();
                CurrentDeviceInfo = new DeviceInfo
                {
                    Id = CurrentDeviceId,
                    Name = deviceName,
                    Address = deviceMacAddress,
                };

                // Register the device with the server
                var response = await HttpClient.PostAsJsonAsync("/api/register", CurrentDeviceInfo);

                if (!response.IsSuccessStatusCode)
                {
                    ShowError($"Failed to register device: {response.StatusCode}");
                    return;
                }

                // Connect to WebSocket server
                var serverUri = $"ws://{ServerConstants.ServerDomain}/ws?uuid={CurrentDeviceId}";
                await ConnectivityService.ConnectAsync(serverUri);

                // Subscribe to incoming messages
                ConnectivityService.MessageReceived += HandleMessageReceived;

                // Scan devices
                await ScanDevices();
            }
            catch (Exception ex)
            {
                ShowError($"Error initializing device: {ex.Message}");
            }
        }

        protected void HandleMessageReceived(object sender, MessageReceivedEventArgs args)
        {
            // Make sure to invoke UI updates on the UI thread
            InvokeAsync(async () =>
            {
                try
                {
                    // Process message based on type
                    switch (args.MessageType)
                    {
                        case "connect":
                            var deviceInfo = JsonSerializer.Deserialize<DeviceInfo>(args.SerializedPayload);
                            if (deviceInfo != null)
                            {
                                var success = await ConnectCurrentDevice(deviceInfo);
                                if (success)
                                {
                                    ShowStatus($"Successfully paired with {deviceInfo.Name}");
                                    await ScanDevices(); // Refresh the device list
                                }
                            }
                            break;

                        case "connect_response":
                            var response = JsonSerializer.Deserialize<ConnectResponse>(args.SerializedPayload);
                            if (response != null && response.Success)
                            {
                                ShowStatus($"Successfully connected to {response.DeviceName}");
                            }
                            else
                            {
                                ShowError($"Failed to connect: {response?.ErrorMessage ?? "Unknown error"}");
                            }
                            break;

                        case "unpair":
                            var unpairDeviceInfo = JsonSerializer.Deserialize<DeviceInfo>(args.SerializedPayload);
                            if (unpairDeviceInfo != null)
                            {
                                await UnpairDeviceLocal(unpairDeviceInfo);
                                ShowStatus($"{unpairDeviceInfo.Name} has unpaired from this device");
                            }
                            break;

                        case "unpair_response":
                            var unpairResponse = JsonSerializer.Deserialize<UnpairResponse>(args.SerializedPayload);
                            if (unpairResponse != null && unpairResponse.Success)
                            {
                                ShowStatus($"Successfully unpaired from {unpairResponse.DeviceName}");
                            }
                            else
                            {
                                ShowError($"Failed to unpair: {unpairResponse?.ErrorMessage ?? "Unknown error"}");
                            }
                            break;

                        case "device_connected":
                            var connectedDevice = JsonSerializer.Deserialize<DeviceInfo>(args.SerializedPayload);
                            if (connectedDevice != null)
                            {
                                // Don't add our own device to the list
                                if (connectedDevice.Id != CurrentDeviceId)
                                {
                                    // Check if the device is paired with us
                                    var pairedDevices = await PairedDevicesService.GetPairedDevicesForId(CurrentDeviceId.ToString());
                                    var deviceId = connectedDevice.Id.ToString();
                                    var isPaired = pairedDevices.Any(pd => pd.IdA == deviceId || pd.IdB == deviceId);

                                    if (isPaired)
                                    {
                                        // Add the device to the online paired devices list if it's not already there
                                        if (!OnlinePairedDevices.Any(d => d.Id == connectedDevice.Id))
                                        {
                                            OnlinePairedDevices.Add(connectedDevice);
                                            ShowStatus($"{connectedDevice.Name} is now online");
                                        }
                                    }
                                }
                            }
                            break;

                        case "device_disconnected":
                            var disconnectedDevice = JsonSerializer.Deserialize<DeviceInfo>(args.SerializedPayload);
                            if (disconnectedDevice != null)
                            {
                                // Remove the device from the online paired devices list
                                var deviceToRemove = OnlinePairedDevices.FirstOrDefault(d => d.Id == disconnectedDevice.Id);
                                if (deviceToRemove != null)
                                {
                                    OnlinePairedDevices.Remove(deviceToRemove);
                                    ShowStatus($"{disconnectedDevice.Name} is now offline");
                                }
                            }
                            break;

                        case "device_metrics_request":
                            var requester = JsonSerializer.Deserialize<DeviceInfo>(args.SerializedPayload);

                            if (requester != null)
                            {
                                // Get the metrics for the current device
                                var metrics = GetDeviceMetrics();
                                // Send the metrics back to the requester
                                await ConnectivityService.SendMessageAsync(
                                    requester.Id.ToString(),
                                    "device_metrics_response",
                                    metrics
                                );
                            }
                            break;

                        case "device_metrics_response":
                            var metricsResponse = JsonSerializer.Deserialize<DeviceMetrics>(args.SerializedPayload);
                            if (metricsResponse != null)
                            {
                                Metrics = metricsResponse;
                            }
                            break;

                        case "error":
                            var error = JsonSerializer.Deserialize<ErrorResponse>(args.SerializedPayload);
                            ShowError($"Error: {error?.Message ?? args.SerializedPayload}");
                            break;

                        default:
                            Console.WriteLine($"Unknown message type: {args.MessageType}, payload: {args.SerializedPayload}");
                            break;
                    }

                    StateHasChanged();
                }
                catch (Exception ex)
                {
                    ShowError($"Error processing message: {ex.Message}");
                }
            });
        }

        protected async Task ScanDevices()
        {
            // Clear previous devices
            Devices.Clear();
            StateHasChanged();

            try
            {
                ShowStatus("Scanning for devices...");

                // Scan for devices on the network
                var response = await HttpClient.GetAsync("/api/connected-devices");

                // Check if the response is successful
                if (response.IsSuccessStatusCode)
                {
                    // Read the response content as a stream
                    var stringContent = await response.Content.ReadAsStringAsync();
                    var devices = JsonSerializer.Deserialize<List<DeviceInfo>>(stringContent);

                    // Get paired devices for the current device
                    var pairedDevices = await PairedDevicesService.GetPairedDevicesForId(CurrentDeviceId.ToString());

                    // Check if the devices are paired
                    if (devices != null)
                    {
                        // Iterate through the devices and check if they are paired
                        foreach (var device in devices)
                        {
                            // Skip if the device is the current device
                            if (device.Id == CurrentDeviceId)
                            {
                                continue;
                            }

                            // Check if the device is paired with the current device
                            var deviceId = device.Id.ToString();
                            var isPaired = pairedDevices.Any(pd => pd.IdA == deviceId || pd.IdB == deviceId);
                            device.IsPaired = isPaired;

                            // Add the device to the list
                            Devices.Add(device);

                            // If the device is paired and online, make sure it's in our OnlinePairedDevices list
                            if (isPaired && !OnlinePairedDevices.Any(d => d.Id == device.Id))
                            {
                                OnlinePairedDevices.Add(device);
                            }
                        }

                        ShowStatus($"Found {Devices.Count} device(s)");
                    }
                    else
                    {
                        ShowStatus("No devices found");
                    }
                }
                else
                {
                    ShowError($"Error scanning devices: {response.StatusCode}");
                }
            }
            catch (Exception ex)
            {
                ShowError($"Error scanning devices: {ex.Message}");
            }

            StateHasChanged();
        }

        protected async Task Connect(DeviceInfo device)
        {
            try
            {
                ShowStatus($"Connecting to {device.Name}...");

                // Send connect message to the target device
                await ConnectivityService.SendMessageAsync(
                    device.Id.ToString(),  // Target device ID
                    "connect",             // Message type
                    CurrentDeviceInfo      // Payload (our device info)
                );

                // Attempt to connect on our side as well
                var connected = await ConnectCurrentDevice(device);
                if (connected)
                {
                    // Send a response back to confirm connection
                    await ConnectivityService.SendMessageAsync(
                        device.Id.ToString(),
                        "connect_response",
                        new ConnectResponse
                        {
                            Success = true,
                            DeviceId = CurrentDeviceId.ToString(),
                            DeviceName = CurrentDeviceInfo.Name
                        }
                    );

                    // Add the device to the online paired devices list if it's not already there
                    if (!OnlinePairedDevices.Any(d => d.Id == device.Id))
                    {
                        OnlinePairedDevices.Add(device);
                    }

                    await ScanDevices(); // Refresh the device list
                }
            }
            catch (Exception ex)
            {
                ShowError($"Error connecting to device: {ex.Message}");
            }
        }

        protected async Task Unpair(DeviceInfo device)
        {
            try
            {
                ShowStatus($"Unpairing from {device.Name}...");

                // Send unpair message to the target device
                await ConnectivityService.SendMessageAsync(
                    device.Id.ToString(),  // Target device ID
                    "unpair",              // Message type
                    CurrentDeviceInfo      // Payload (our device info)
                );

                // Unpair on our side as well
                await UnpairDeviceLocal(device);

                // Send a response back to confirm unpairing
                await ConnectivityService.SendMessageAsync(
                    device.Id.ToString(),
                    "unpair_response",
                    new UnpairResponse
                    {
                        Success = true,
                        DeviceId = CurrentDeviceId.ToString(),
                        DeviceName = CurrentDeviceInfo.Name
                    }
                );

                // Refresh the device list
                await ScanDevices();
            }
            catch (Exception ex)
            {
                ShowError($"Error unpairing device: {ex.Message}");
            }
        }

        protected async Task UnpairDeviceLocal(DeviceInfo device)
        {
            try
            {
                // Get the current device's ID and the target device's ID
                var currentDeviceId = CurrentDeviceId.ToString();
                var targetDeviceId = device.Id.ToString();

                // Remove the pairing from the database
                await PairedDevicesService.RemovePairedDevicesAsync(currentDeviceId, targetDeviceId);

                // Remove the device from the online paired devices list
                var deviceToRemove = OnlinePairedDevices.FirstOrDefault(d => d.Id == device.Id);
                if (deviceToRemove != null)
                {
                    OnlinePairedDevices.Remove(deviceToRemove);
                }

                // Update the device's paired status in the UI
                var deviceInList = Devices.FirstOrDefault(d => d.Id == device.Id);
                if (deviceInList != null)
                {
                    deviceInList.IsPaired = false;
                }

                ShowStatus($"Unpaired from {device.Name}");
                StateHasChanged();
            }
            catch (Exception ex)
            {
                ShowError($"Error unpairing device locally: {ex.Message}");
            }
        }

        protected async Task<bool> ConnectCurrentDevice(DeviceInfo device)
        {
            try
            {
                // Get the current device's ID and the target device's ID
                var currentDeviceId = CurrentDeviceId.ToString();
                var targetDeviceId = device.Id.ToString();

                // Create a new device mapping for the target device
                var targetDeviceMapping = new DeviceMapping
                {
                    MacAddress = device.Address,
                    Id = targetDeviceId,
                };

                // Pair the devices and save the mapping
                var pairingResponse = await PairedDevicesService.SavePairedDevicesAsync(currentDeviceId, targetDeviceId);
                var mappingResponse = await DeviceMappingService.SaveMappingAsync(targetDeviceMapping);

                // Update the device's paired status in the UI
                if (pairingResponse && mappingResponse > 0)
                {
                    device.IsPaired = true;
                    StateHasChanged();
                    return true;
                }

                return false;
            }
            catch (Exception ex)
            {
                ShowError($"Error pairing with device: {ex.Message}");
                return false;
            }
        }

        protected DeviceMetrics GetDeviceMetrics()
        {
            return new DeviceMetrics
            {
                CpuUsage = Math.Round(DeviceMetricService.GetCpuUsage(), 2),
                RamUsage = Math.Round(DeviceMetricService.GetRamUsage(), 2),
                StorageUsage = Math.Round(DeviceMetricService.GetStorageUsage(), 2),
                NetworkUp = Math.Round(DeviceMetricService.GetNetworkUploadSpeed(), 2),
                NetworkDown = Math.Round(DeviceMetricService.GetNetworkDownloadSpeed(), 2)
            };
        }

        protected async Task RequestMetricsAsync(DeviceInfo device)
        {
            try
            {
                // Send a request for metrics to the target device
                await ConnectivityService.SendMessageAsync(
                    device.Id.ToString(),
                    "device_metrics_request",
                    CurrentDeviceInfo
                );
            }
            catch (Exception ex)
            {
                ShowError($"Error requesting metrics: {ex.Message}");
            }
        }

        protected bool IsDeviceOnline(Guid deviceId)
        {
            return OnlinePairedDevices.Any(d => d.Id == deviceId);
        }

        protected void ShowStatus(string message)
        {
            StatusMessage = message;
            IsError = false;
            StateHasChanged();
        }

        protected void ShowError(string message)
        {
            StatusMessage = message;
            IsError = true;
            StateHasChanged();
        }

        public async Task DisposeAsync()
        {
            // Unsubscribe from the event when the component is disposed
            ConnectivityService.MessageReceived -= HandleMessageReceived;
            await ConnectivityService.DisconnectAsync();
            _cts?.Cancel();
            _cts?.Dispose();
        }
    }
}
