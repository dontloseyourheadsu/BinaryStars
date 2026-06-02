import { useState, useEffect } from "react";
import { listen } from "@tauri-apps/api/event";
import {
  getBluetoothStatus,
  startBluetoothServer,
  stopBluetoothServer,
  connectBluetoothDevice,
  getBluetoothDevices,
  scanBluetoothDevices,
  LinuxBluetoothDevice,
} from "./features/discovery/discoveryApi";
import { DiscoveryPanel } from "./features/discovery/DiscoveryPanel";
import { ChatPanel } from "./features/chat/ChatPanel";
import "./core/theme.css";
import "./App.css";

function App() {
  const [isDark, setIsDark] = useState(true);
  const [selfId] = useState(() => {
    const saved = localStorage.getItem("binarystars_self_id");
    if (saved) return saved;
    const newId = `Linux-${Math.random().toString(36).substring(2, 8).toUpperCase()}`;
    localStorage.setItem("binarystars_self_id", newId);
    return newId;
  });

  const [connected, setConnected] = useState(false);
  const [peerId, setPeerId] = useState("");
  const [isHosting, setIsHosting] = useState(false);
  const [isScanning, setIsScanning] = useState(false);
  const [isConnecting, setIsConnecting] = useState(false);
  const [devices, setDevices] = useState<LinuxBluetoothDevice[]>([]);

  // Monitor connection states
  useEffect(() => {
    getBluetoothStatus()
      .then((status) => {
        setIsHosting(status.serverRunning);
        if (status.connectedDeviceId) {
          setConnected(true);
          setPeerId(status.connectedDeviceId);
        }
      })
      .catch(console.error);

    const unlistenStatusPromise = listen("bluetooth-status", (event) => {
      const payload = event.payload as {
        connected: boolean;
        deviceId: string | null;
        deviceAddress: string | null;
      };
      
      setConnected(payload.connected);
      setPeerId(payload.deviceId || "");
      setIsConnecting(false);
      
      if (payload.connected) {
        setIsHosting(false); // Stop hosting once connected
      }
    });

    return () => {
      unlistenStatusPromise.then((unlisten) => unlisten());
    };
  }, []);

  const updateDevicesList = async () => {
    try {
      const list = await getBluetoothDevices();
      setDevices(list);
    } catch (e) {
      console.error("Failed to query Bluetooth devices", e);
    }
  };

  const handleScanToggle = async () => {
    if (isScanning) return;
    setIsScanning(true);
    try {
      await scanBluetoothDevices();
      await updateDevicesList();
    } catch (e: any) {
      alert(`Scanning failed: ${e}`);
    } finally {
      setIsScanning(false);
    }
  };

  const handleHostToggle = async () => {
    if (isHosting) {
      try {
        await stopBluetoothServer();
        setIsHosting(false);
      } catch (e: any) {
        alert(`Stop server failed: ${e}`);
      }
    } else {
      try {
        await startBluetoothServer(selfId);
        setIsHosting(true);
      } catch (e: any) {
        alert(`Host server failed: ${e}`);
      }
    }
  };

  const handleConnect = async (device: LinuxBluetoothDevice) => {
    setIsConnecting(true);
    try {
      await connectBluetoothDevice(selfId, device.address);
    } catch (e: any) {
      alert(`Connection failed: ${e}`);
      setIsConnecting(false);
    }
  };

  const handleDisconnect = async () => {
    try {
      await stopBluetoothServer();
      setConnected(false);
      setPeerId("");
    } catch (e: any) {
      alert(`Disconnect failed: ${e}`);
    }
  };

  const themeClass = isDark ? "dark-theme" : "light-theme";

  return (
    <div className={`app-root ${themeClass}`}>
      {/* App Header Bar */}
      <header className="app-header">
        <div className="header-brand">
          <h1>BINARY STARS</h1>
          <span className="self-info">ID: {selfId}</span>
        </div>
        <button onClick={() => setIsDark(!isDark)} className="btn-theme-toggle">
          {isDark ? "🌙" : "☀️"}
        </button>
      </header>

      {/* Main Content Area */}
      <main className="app-main-content">
        {isConnecting ? (
          <div className="connecting-view">
            <span className="connecting-spinner"></span>
            <h3>Establishing Connection...</h3>
            <p>Exchanging identity handshakes</p>
          </div>
        ) : connected ? (
          <ChatPanel
            isDark={isDark}
            peerId={peerId}
            onDisconnect={handleDisconnect}
          />
        ) : (
          <DiscoveryPanel
            isDark={isDark}
            devices={devices}
            isScanning={isScanning}
            isHosting={isHosting}
            onScanToggle={handleScanToggle}
            onHostToggle={handleHostToggle}
            onConnect={handleConnect}
          />
        )}
      </main>
    </div>
  );
}

export default App;
