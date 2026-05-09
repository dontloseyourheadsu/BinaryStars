import { useState, useEffect, useCallback, useRef } from "react";
import { api } from "../api";
import { Device } from "../types";
import { getLocalDeviceInfo } from "../tauriDeviceInfo";
import { cacheStore } from "../storage";

const API_TIMEOUT_MS = 5000;

async function withTimeout<T>(promise: Promise<T>, timeoutMs: number): Promise<T> {
  let timer: number | null = null;
  const timeoutPromise = new Promise<T>((_, reject) => {
    timer = window.setTimeout(() => reject(new Error("API Timeout")), timeoutMs);
  });
  
  try {
    const result = await Promise.race([promise, timeoutPromise]);
    return result;
  } finally {
    if (timer != null) window.clearTimeout(timer);
  }
}

export function useDevices(
  isAuthed: boolean, 
  online: boolean, 
  myDeviceId: string,
  isDeviceBluetoothOnline: (device: Device) => boolean,
  setError: (msg: string | null) => void
) {
  // Initialize from cache
  const [devices, setDevicesInternal] = useState<Device[]>(cacheStore.getDevices());
  const [localMemoryLoadPercent, setLocalMemoryLoadPercent] = useState<number | null>(null);
  const isRefreshingRef = useRef(false);

  // Wrapper to ensure storage sync
  const setDevices = useCallback((next: Device[] | ((prev: Device[]) => Device[])) => {
    setDevicesInternal(prev => {
      const updated = typeof next === "function" ? next(prev) : next;
      cacheStore.setDevices(updated);
      return updated;
    });
  }, []);

  const refreshDevices = useCallback(async (surfaceError = false): Promise<void> => {
    if (!isAuthed || isRefreshingRef.current) return;
    
    try {
      isRefreshingRef.current = true;
      const next = await withTimeout(api.getDevices(), API_TIMEOUT_MS);
      setDevicesInternal(next.map((device) => ({
        ...device,
        isBluetoothOnline: isDeviceBluetoothOnline(device),
      })));
    } catch (e) {
      console.error("Failed to refresh devices", e);
      if (surfaceError) setError("Failed to reach API. Some info might be stale.");
      
      // Fallback: update bluetooth status on existing devices
      setDevicesInternal(prev => prev.map(d => ({
        ...d,
        isBluetoothOnline: isDeviceBluetoothOnline(d)
      })));
    } finally {
      isRefreshingRef.current = false;
    }
  }, [isAuthed, isDeviceBluetoothOnline, setError]);

  // Telemetry Sync Loop
  useEffect(() => {
    if (!isAuthed || !online) return;

    const runSync = async () => {
      try {
        const local = await getLocalDeviceInfo();
        setLocalMemoryLoadPercent(local.memory_load_percent ?? null);
        
        await withTimeout(api.updateTelemetry(myDeviceId, {
          batteryLevel: local.battery_level ?? 100,
          cpuLoadPercent: local.cpu_load_percent ?? 0,
          memoryLoadPercent: local.memory_load_percent ?? null,
          isOnline: true,
          isAvailable: true,
          isSynced: true,
          wifiUploadSpeed: local.wifi_upload_speed,
          wifiDownloadSpeed: local.wifi_download_speed,
        }), API_TIMEOUT_MS);
        
        await refreshDevices();
      } catch (e) {
        console.warn("Telemetry sync failed", e);
      }
    };

    runSync();
    const timer = window.setInterval(runSync, 10_000);
    return () => window.clearInterval(timer);
  }, [isAuthed, online, myDeviceId, refreshDevices]);

  // Bluetooth status sync only (high frequency)
  useEffect(() => {
    setDevicesInternal(prev => prev.map(d => ({
      ...d,
      isBluetoothOnline: isDeviceBluetoothOnline(d)
    })));
  }, [isDeviceBluetoothOnline]);

  return {
    devices,
    setDevices,
    localMemoryLoadPercent,
    setLocalMemoryLoadPercent,
    refreshDevices
  };
}
