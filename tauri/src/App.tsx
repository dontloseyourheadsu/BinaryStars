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
import { getRecentChats, RecentChat } from "./features/chat/chatApi";
import { TabletPanel } from "./features/tablet/TabletPanel";
import { KeyboardPanel } from "./features/keyboard/KeyboardPanel";
import { MousepadPanel } from "./features/mousepad/MousepadPanel";
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
  const [recentChats, setRecentChats] = useState<RecentChat[]>([]);
  const [viewingHistoryPeerId, setViewingHistoryPeerId] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<"discovery" | "chat" | "tablet" | "keyboard" | "mousepad">("discovery");


  const updateRecentChats = async () => {
    try {
      const recents = await getRecentChats();
      setRecentChats(recents);
    } catch (e) {
      console.error("Failed to query recent chats", e);
    }
  };

  useEffect(() => {
    updateRecentChats();
  }, [connected, viewingHistoryPeerId]);

  // Monitor connection states
  useEffect(() => {
    getBluetoothStatus()
      .then((status) => {
        setIsHosting(status.serverRunning);
        if (status.connectedDeviceId) {
          setConnected(true);
          setPeerId(status.connectedDeviceId);
          setActiveTab("chat");
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
        setActiveTab("chat");
      } else {
        setActiveTab("discovery");
      }
    });

    return () => {
      unlistenStatusPromise.then((unlisten) => unlisten());
    };
  }, []);

  // Listen for open-chat events from notification click
  useEffect(() => {
    const unlistenOpenChatPromise = listen<string>("open-chat", (event) => {
      const targetPeerId = event.payload;
      setViewingHistoryPeerId(targetPeerId);
    });

    return () => {
      unlistenOpenChatPromise.then((unlisten) => unlisten());
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

  const [pwdModal, setPwdModal] = useState<{
    isOpen: boolean;
    title: string;
    description: string;
    isOptional: boolean;
    onSubmit: (pwd: string) => void;
  }>({
    isOpen: false,
    title: "",
    description: "",
    isOptional: true,
    onSubmit: () => {},
  });
  const [pwdValue, setPwdValue] = useState("");

  const handleHostToggle = async () => {
    if (isHosting) {
      try {
        await stopBluetoothServer();
        setIsHosting(false);
        setActiveTab("discovery");
      } catch (e: any) {
        alert(`Stop server failed: ${e}`);
      }
    } else {
      setPwdModal({
        isOpen: true,
        title: "Set Host Password",
        description: "Choose an optional password to restrict who can connect. Leave blank for no password.",
        isOptional: true,
        onSubmit: async (pwd) => {
          try {
            await startBluetoothServer(selfId, pwd || undefined);
            setIsHosting(true);
            setActiveTab("chat");
          } catch (e: any) {
            alert(`Host server failed: ${e}`);
          }
        }
      });
    }
  };

  const handleConnect = async (device: LinuxBluetoothDevice) => {
    setPwdModal({
      isOpen: true,
      title: "Join Password",
      description: `Enter the password to connect to ${device.name}. Leave blank if no password is required.`,
      isOptional: true,
      onSubmit: async (pwd) => {
        setIsConnecting(true);
        try {
          await connectBluetoothDevice(selfId, device.address, pwd || undefined);
        } catch (e: any) {
          alert(`Connection failed: ${e}`);
          setIsConnecting(false);
        }
      }
    });
  };

  const handleDisconnect = async () => {
    try {
      await stopBluetoothServer();
      setConnected(false);
      setPeerId("");
      setActiveTab("discovery");
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

      {/* Navigation Bar */}
      {!isConnecting && (
        <nav className="app-nav-bar">
          <button
            onClick={() => setActiveTab("discovery")}
            className={`nav-tab-btn ${activeTab === "discovery" ? "active" : ""}`}
          >
            <span>📶</span> Discovery
          </button>
          <button
            onClick={() => setActiveTab("chat")}
            className={`nav-tab-btn ${activeTab === "chat" ? "active" : ""}`}
          >
            <span>💬</span> Chat Room
          </button>
          <button
            onClick={() => setActiveTab("tablet")}
            className={`nav-tab-btn ${activeTab === "tablet" ? "active" : ""}`}
          >
            <span>🎨</span> Tablet Mode
          </button>
          <button
            onClick={() => setActiveTab("keyboard")}
            className={`nav-tab-btn ${activeTab === "keyboard" ? "active" : ""}`}
          >
            <span>⌨️</span> Keyboard Mode
          </button>
          <button
            onClick={() => setActiveTab("mousepad")}
            className={`nav-tab-btn ${activeTab === "mousepad" ? "active" : ""}`}
          >
            <span>🖱️</span> Mousepad Mode
          </button>
        </nav>
      )}

      {/* Main Content Area */}
      <main className="app-main-content">
        {isConnecting ? (
          <div className="connecting-view">
            <span className="connecting-spinner"></span>
            <h3>Establishing Connection...</h3>
            <p>Exchanging identity handshakes</p>
          </div>
        ) : activeTab === "chat" ? (
          connected ? (
            <ChatPanel
              isDark={isDark}
              peerId={peerId}
              onDisconnect={handleDisconnect}
            />
          ) : isHosting ? (
            <ChatPanel
              isDark={isDark}
              peerId="Group Chat Session"
              onDisconnect={handleDisconnect}
            />
          ) : (
            <div className="empty-state" style={{ flexDirection: "column", gap: "12px", textAlign: "center", padding: "40px" }}>
              <span style={{ fontSize: "36px" }}>📡</span>
              <h3>No Active Connection</h3>
              <p style={{ maxWidth: "320px", fontSize: "13px", opacity: 0.7 }}>
                The chat room becomes available once you connect to a nearby peer or start hosting a session.
              </p>
              <button onClick={() => setActiveTab("discovery")} className="btn-host" style={{ marginTop: "8px" }}>
                Go to Discovery
              </button>
            </div>
          )
        ) : activeTab === "tablet" ? (
          <TabletPanel
            isDark={isDark}
            connected={connected}
            peerId={peerId}
          />
        ) : activeTab === "keyboard" ? (
          <KeyboardPanel
            isDark={isDark}
            connected={connected}
            peerId={peerId}
          />
        ) : activeTab === "mousepad" ? (
          <MousepadPanel
            isDark={isDark}
            connected={connected}
            peerId={peerId}
          />
        ) : (
          /* activeTab === "discovery" */
          viewingHistoryPeerId ? (
            <ChatPanel
              isDark={isDark}
              peerId={viewingHistoryPeerId}
              isOffline={true}
              onDisconnect={() => setViewingHistoryPeerId(null)}
            />
          ) : (
            <DiscoveryPanel
              isDark={isDark}
              devices={devices}
              recentChats={recentChats}
              isScanning={isScanning}
              isHosting={isHosting}
              onScanToggle={handleScanToggle}
              onHostToggle={handleHostToggle}
              onConnect={handleConnect}
              onViewHistory={setViewingHistoryPeerId}
            />
          )
        )}
      </main>


      {pwdModal.isOpen && (
        <div className="pwd-modal-overlay" onClick={() => {
          setPwdModal(prev => ({ ...prev, isOpen: false }));
          setPwdValue("");
        }}>
          <div className="pwd-modal-card glass-card dark" onClick={(e) => e.stopPropagation()}>
            <h3>{pwdModal.title}</h3>
            <p>{pwdModal.description}</p>
            <input
              type="password"
              value={pwdValue}
              onChange={(e) => setPwdValue(e.target.value)}
              placeholder="Enter password..."
              className="pwd-modal-input"
              autoFocus
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  pwdModal.onSubmit(pwdValue);
                  setPwdModal(prev => ({ ...prev, isOpen: false }));
                  setPwdValue("");
                }
              }}
            />
            <div className="pwd-modal-actions">
              <button
                onClick={() => {
                  setPwdModal(prev => ({ ...prev, isOpen: false }));
                  setPwdValue("");
                }}
                className="btn-pwd-cancel"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  pwdModal.onSubmit(pwdValue);
                  setPwdModal(prev => ({ ...prev, isOpen: false }));
                  setPwdValue("");
                }}
                className="btn-pwd-submit"
              >
                {pwdModal.isOptional && !pwdValue ? "Skip / Continue" : "Confirm"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default App;
