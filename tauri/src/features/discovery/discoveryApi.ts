import { invoke } from "@tauri-apps/api/core";

export interface LinuxBluetoothDevice {
  name: string;
  address: string;
  connected: boolean;
  paired: boolean;
}

export interface BluetoothStatus {
  serverRunning: boolean;
  connectedDeviceId: string | null;
  connectedDeviceAddress: string | null;
}

export async function getBluetoothStatus(): Promise<BluetoothStatus> {
  return await invoke<BluetoothStatus>("get_bluetooth_status");
}

export async function startBluetoothServer(myDeviceId: string, password?: string): Promise<string> {
  return await invoke<string>("start_bluetooth_server", { myDeviceId, password });
}

export async function stopBluetoothServer(): Promise<void> {
  return await invoke<void>("stop_bluetooth_server");
}

export async function connectBluetoothDevice(myDeviceId: string, deviceAddress: string, password?: string): Promise<string> {
  return await invoke<string>("connect_bluetooth_device", { myDeviceId, deviceAddress, password });
}

export async function getBluetoothDevices(): Promise<LinuxBluetoothDevice[]> {
  return await invoke<LinuxBluetoothDevice[]>("get_bluetooth_devices");
}

export async function scanBluetoothDevices(): Promise<void> {
  return await invoke<void>("scan_bluetooth_devices");
}

export async function getBluetoothConnectedDeviceNames(): Promise<string[]> {
  return await invoke<string[]>("get_bluetooth_connected_device_names");
}
