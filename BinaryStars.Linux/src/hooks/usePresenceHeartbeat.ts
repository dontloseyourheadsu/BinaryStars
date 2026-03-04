import { useEffect } from "react";
import { api } from "../api";
import type { Device } from "../types";

/**
 * Sends mandatory device heartbeat updates independent from optional feature hooks.
 */
export function usePresenceHeartbeat(
  isAuthed: boolean,
  deviceId: string,
  onHeartbeat?: (device: Device) => void,
): void {
  useEffect(() => {
    if (!isAuthed) {
      return;
    }

    const runHeartbeat = async (): Promise<void> => {
      try {
        const device = await api.sendHeartbeat(deviceId);
        onHeartbeat?.(device);
      } catch {
        // ignore heartbeat failures and retry on next interval
      }
    };

    void runHeartbeat();
    const timer = window.setInterval(() => {
      void runHeartbeat();
    }, 10_000);

    return () => window.clearInterval(timer);
  }, [isAuthed, deviceId, onHeartbeat]);
}
