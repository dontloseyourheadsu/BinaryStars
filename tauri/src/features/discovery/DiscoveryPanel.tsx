import React from "react";
import { LinuxBluetoothDevice } from "./discoveryApi";
import "../../features/features.css";

interface DiscoveryPanelProps {
  isDark: boolean;
  devices: LinuxBluetoothDevice[];
  isScanning: boolean;
  isHosting: boolean;
  onScanToggle: () => void;
  onHostToggle: () => void;
  onConnect: (device: LinuxBluetoothDevice) => void;
}

export const DiscoveryPanel: React.FC<DiscoveryPanelProps> = ({
  isDark,
  devices,
  isScanning,
  isHosting,
  onScanToggle,
  onHostToggle,
  onConnect,
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
