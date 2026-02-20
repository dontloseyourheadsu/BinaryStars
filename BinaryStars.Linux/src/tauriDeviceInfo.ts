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

function isTauriRuntime(): boolean {
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
