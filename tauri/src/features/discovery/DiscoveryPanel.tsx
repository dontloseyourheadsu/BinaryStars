import React from "react";
import { LinuxBluetoothDevice } from "./discoveryApi";
import { RecentChat } from "../chat/chatApi";
import "../../features/features.css";

interface DiscoveryPanelProps {
  isDark: boolean;
  devices: LinuxBluetoothDevice[];
  recentChats: RecentChat[];
  isScanning: boolean;
  isHosting: boolean;
  onScanToggle: () => void;
  onHostToggle: () => void;
  onConnect: (device: LinuxBluetoothDevice) => void;
  onViewHistory: (peerId: string) => void;
}

export const DiscoveryPanel: React.FC<DiscoveryPanelProps> = ({
  isDark,
  devices,
  recentChats,
  isScanning,
  isHosting,
  onScanToggle,
  onHostToggle,
  onConnect,
  onViewHistory,
}) => {
  return (
    <div className="dashboard-container">
      {/* Hosting Section */}
      <div className={`glass-card ${isDark ? "dark" : "light"}`}>
        <div className="host-row">
          <div className="host-info">
            <h3>Receive Connections</h3>
            <p className={isHosting ? "hosting-active" : "hosting-idle"}>
              {isHosting ? "Listening for incoming signals..." : "Off - device is invisible"}
            </p>
          </div>
          <button
            onClick={onHostToggle}
            className={`btn-host ${isHosting ? "active" : ""}`}
          >
            {isHosting ? "HOSTING" : "HOST"}
          </button>
        </div>
      </div>

      {/* Recent Chats Section */}
      <div className="section-header">
        <h2>Recent Chats</h2>
      </div>
      <div className="recents-list">
        {recentChats.length === 0 ? (
          <div className="empty-state" style={{ height: "60px" }}>
            <p>No recent cosmic communications. Scan to start a chat!</p>
          </div>
        ) : (
          recentChats.map((chat) => (
            <div
              key={chat.peerId}
              onClick={() => onViewHistory(chat.peerId)}
              className={`glass-card recent-item ${isDark ? "dark" : "light"}`}
            >
              <div className="recent-icon">
                <span>💬</span>
              </div>
              <div className="recent-info">
                <div className="recent-meta">
                  <h4>{chat.peerId}</h4>
                  <span className="recent-time">
                    {new Date(chat.lastMsgAt).toLocaleString([], { hour: '2-digit', minute: '2-digit', month: 'short', day: 'numeric' })}
                  </span>
                </div>
                <p className="recent-preview">{chat.lastMessage}</p>
              </div>
            </div>
          ))
        )}
      </div>

      {/* Discovery Section Header */}
      <div className="section-header">
        <h2>Nearby Devices</h2>
        <button
          onClick={onScanToggle}
          className={`btn-scan ${isScanning ? "scanning" : ""}`}
        >
          {isScanning ? (
            <div className="scanner-container">
              <span className="spinner"></span>
              <span>SCANNING</span>
            </div>
          ) : (
            "SCAN"
          )}
        </button>
      </div>

      {/* Scanned Devices List */}
      <div className="devices-list">
        {devices.length === 0 ? (
          <div className="empty-state">
            <p>{isScanning ? "Searching for signals in the void..." : "No cosmic nodes active. Click SCAN."}</p>
          </div>
        ) : (
          devices.map((dev) => (
            <div
              key={dev.address}
              onClick={() => onConnect(dev)}
              className={`glass-card device-item ${isDark ? "dark" : "light"}`}
            >
              <div className="device-icon">
                <span>📶</span>
              </div>
              <div className="device-info">
                <h4>{dev.name}</h4>
                <p>{dev.address}</p>
              </div>
              {dev.paired && <span className="paired-badge">PAIRED</span>}
            </div>
          ))
        )}
      </div>
    </div>
  );
};
