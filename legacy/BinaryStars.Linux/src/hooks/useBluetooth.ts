import { useState, useEffect, useMemo, useCallback } from "react";
import { getBluetoothDevices, scanBluetoothDevices, verifyBluetoothIdentity } from "../tauriDeviceInfo";
import { Device } from "../types";
import { cacheStore } from "../storage";

export function useBluetooth(isAuthed: boolean, myDeviceId: string, myDeviceName: string) {
  const [bluetoothConnectedNames, setBluetoothConnectedNames] = useState<string[]>([]);
  const [isScanning, setIsScanning] = useState(false);

  const refresh = useCallback(async () => {
    try {
      const knownDevices = await getBluetoothDevices();
      const connectedPeers = knownDevices.filter((entry) => entry.connected && entry.address);
      const names = connectedPeers.map((entry) => entry.name);
      setBluetoothConnectedNames(names);

      // Proactive Identity Mapping: Probe connected but unmapped devices
      if (isAuthed) {
        const idMap = cacheStore.getBluetoothIdMap();
        const mappedMacs = new Set(Object.values(idMap).map(m => m.macAddress));
        const accountDevices = cacheStore.getDevices();

        for (const peer of connectedPeers) {
          if (mappedMacs.has(peer.address)) continue;

          // If we find an unmapped connected peer, try to find which Account Device it is
          for (const accDevice of accountDevices) {
            if (accDevice.id === myDeviceId) continue;
            if (idMap[accDevice.id]) continue; // Already mapped to a different MAC

            // Identity probe
            const isMatch = await verifyBluetoothIdentity(peer.address, myDeviceId, myDeviceName, accDevice.id);
            if (isMatch) {
              const nextMap = { ...cacheStore.getBluetoothIdMap() };
              nextMap[accDevice.id] = {
                deviceId: accDevice.id,
                macAddress: peer.address,
                lastVerifiedAt: new Date().toISOString()
              };
              cacheStore.setBluetoothIdMap(nextMap);
              break; 
            }
          }
        }
      }
    } catch (e) {
      console.error("Failed to refresh bluetooth presence", e);
    }
  }, [isAuthed, myDeviceId, myDeviceName]);

  useEffect(() => {
    if (!isAuthed) return;
    refresh();
    const timer = window.setInterval(refresh, 10_000);
    return () => window.clearInterval(timer);
  }, [isAuthed, refresh]);

  const scan = useCallback(async () => {
    if (isScanning) return;
    setIsScanning(true);
    try {
      await scanBluetoothDevices();
      await refresh();
    } finally {
      setIsScanning(false);
    }
  }, [isScanning, refresh]);

  const isBluetoothConnected = useMemo(() => {
    return bluetoothConnectedNames.length > 0;
  }, [bluetoothConnectedNames]);

  const findBestBluetoothAddress = useCallback(async (
    targetDevice: Device, 
    myDeviceId: string, 
    myDeviceName: string
  ): Promise<string | null> => {
    const idMap = cacheStore.getBluetoothIdMap();
    
    // 1. Check if we already have a verified mapping for this Device ID
    if (idMap[targetDevice.id]) {
      const mapping = idMap[targetDevice.id];
      // Optional: Check freshness here. For now, assume it's valid if they are connected.
      const bluetoothPeers = await getBluetoothDevices();
      const isStillConnected = bluetoothPeers.some(p => p.connected && p.address === mapping.macAddress);
      if (isStillConnected) return mapping.macAddress;
    }

    // 2. If no mapping, we PROBE all currently connected Bluetooth devices
    const bluetoothPeers = await getBluetoothDevices();
    const connectedPeers = bluetoothPeers.filter(p => p.connected && p.address);
    
    if (connectedPeers.length === 0) return null;

    for (const peer of connectedPeers) {
      // Attempt a non-destructive identity verification handshake
      const isMatch = await verifyBluetoothIdentity(
        peer.address,
        myDeviceId,
        myDeviceName,
        targetDevice.id
      );

      if (isMatch) {
        // SUCCESS! Cache the mapping so we don't have to probe again next time.
        const nextMap = { ...idMap };
        nextMap[targetDevice.id] = {
          deviceId: targetDevice.id,
          macAddress: peer.address,
          lastVerifiedAt: new Date().toISOString()
        };
        cacheStore.setBluetoothIdMap(nextMap);
        return peer.address;
      }
    }

    return null;
  }, []);

  const isDeviceBluetoothOnline = useCallback((device: Device): boolean => {
    // 1. Check Identity Map first (Source of Truth)
    const idMap = cacheStore.getBluetoothIdMap();
    if (idMap[device.id]) {
      // If we have a verified mapping, check if that specific MAC is currently connected
      // For now, if it's in the map, and we have ANY bluetooth connected, it's a strong indicator.
      if (bluetoothConnectedNames.length > 0) return true;
    }

    // 2. Fallback to Precise Match (if no identity map yet)
    const bluetoothSet = new Set(bluetoothConnectedNames);
    if (bluetoothSet.has(device.name)) return true;

    // 3. Case-insensitive & Fuzzy Match
    const normalizedTarget = device.name.trim().toLowerCase();
    const fuzzyMatch = bluetoothConnectedNames.some(name => {
      const normalizedName = name.trim().toLowerCase();
      return normalizedName === normalizedTarget || 
             normalizedName.includes(normalizedTarget) || 
             normalizedTarget.includes(normalizedName);
    });
    if (fuzzyMatch) return true;

    return false;
  }, [bluetoothConnectedNames]);

  return {
    bluetoothConnectedNames,
    isBluetoothConnected,
    isScanning,
    scan,
    findBestBluetoothAddress,
    isDeviceBluetoothOnline
  };
}
