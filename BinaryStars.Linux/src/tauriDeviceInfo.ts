import { invoke } from "@tauri-apps/api/core";

export interface TauriInterfaceInfo {
  name: string;
  ips: string[];
  rx_bytes_per_sec: number;
  tx_bytes_per_sec: number;
}

export interface TauriDeviceInfo {
  hostname: string;
  ip_address: string;
  ipv6_address: string;
  battery_level: number | null;
  cpu_load_percent: number | null;
  memory_load_percent: number | null;
  wifi_upload_speed: string;
  wifi_download_speed: string;
  interfaces: TauriInterfaceInfo[];
}

export interface NativeLocation {
  latitude: number;
  longitude: number;
  accuracyMeters: number | null;
}

export interface ElevationStatus {
  is_root: boolean;
}

export function isTauriRuntime(): boolean {
  return typeof window !== "undefined" && "__TAURI_INTERNALS__" in window;
}

export async function getLocalDeviceInfo(): Promise<TauriDeviceInfo> {
  if (!isTauriRuntime()) {
    const fallbackName = navigator.userAgent.includes("Linux") ? "Linux Desktop" : "Desktop Client";
    return {
      hostname: fallbackName,
      ip_address: "127.0.0.1",
      ipv6_address: "::1",
      battery_level: null,
      cpu_load_percent: null,
      memory_load_percent: null,
      wifi_upload_speed: "0 kbps",
      wifi_download_speed: "0 kbps",
      interfaces: [],
    };
  }

  return invoke<TauriDeviceInfo>("get_device_info");
}

export async function getBluetoothConnectedDeviceNames(): Promise<string[]> {
  if (!isTauriRuntime()) {
    return [];
  }

  try {
    return await invoke<string[]>("get_bluetooth_connected_device_names");
  } catch {
    return [];
  }
}

export async function isWifiConnected(): Promise<boolean> {
  if (!isTauriRuntime()) {
    return true;
  }

  try {
    return await invoke<boolean>("is_wifi_connected");
  } catch {
    return false;
  }
}

export async function getNativeLocation(): Promise<NativeLocation | null> {
  if (!isTauriRuntime()) {
    return null;
  }

  try {
    const result = await invoke<{
      latitude: number;
      longitude: number;
      accuracy_meters: number | null;
    }>("get_native_location");

    return {
      latitude: result.latitude,
      longitude: result.longitude,
      accuracyMeters: result.accuracy_meters,
    };
  } catch {
    return null;
  }
}

export async function performLocalAction(
  actionType:
    | "block_screen"
    | "shutdown"
    | "reboot"
    | "list_launchable_apps"
    | "list_running_apps"
    | "open_app"
    | "close_app"
    | "get_clipboard_history",
  payloadJson?: string | null,
): Promise<string> {
  if (!isTauriRuntime()) {
    throw new Error("Local actions are supported only in the Linux desktop app runtime");
  }

  return invoke<string>("perform_local_action", { actionType, payloadJson });
}

export async function getElevationStatus(): Promise<ElevationStatus> {
  if (!isTauriRuntime()) {
    return { is_root: false };
  }

  return invoke<ElevationStatus>("get_elevation_status");
}

export async function requestElevatedMode(): Promise<void> {
  if (!isTauriRuntime()) {
    throw new Error("Elevation is supported only in the Linux desktop app runtime");
  }

  await invoke<void>("request_elevated_mode");
}
