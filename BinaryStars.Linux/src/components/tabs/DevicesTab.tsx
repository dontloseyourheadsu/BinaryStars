import type { Device } from "../../types";

type Props = {
  onlineDevices: Device[];
  offlineDevices: Device[];
  currentDevice: Device | null;
  selectedDevice: Device | null;
  deviceAlias: string;
  onDeviceAliasChange: (value: string) => void;
  onSelectDevice: (deviceId: string) => void;
  onCloseDeviceDetail: () => void;
  onOpenChat: (deviceId: string) => void;
  onLinkCurrentDevice: () => void;
  onUnlinkCurrentDevice: () => void;
  onUnlinkDevice: (deviceId: string) => void;
  formatRam: (device: Device) => string;
  formatBattery: (device: Device) => string;
  clipboardHistory: string[];
  clipboardHistoryLoading: boolean;
  clipboardHistoryMessage: string;
  onRefreshClipboardHistory: () => void;
  onCopyClipboardHistoryEntry: (value: string) => void;
};

export default function DevicesTab({
  onlineDevices,
  offlineDevices,
  currentDevice,
  selectedDevice,
  deviceAlias,
  onDeviceAliasChange,
  onSelectDevice,
  onCloseDeviceDetail,
  onOpenChat,
  onLinkCurrentDevice,
  onUnlinkCurrentDevice,
  onUnlinkDevice,
  formatRam,
  formatBattery,
  clipboardHistory,
  clipboardHistoryLoading,
  clipboardHistoryMessage,
  onRefreshClipboardHistory,
  onCopyClipboardHistoryEntry,
}: Props) {
  if (selectedDevice) {
    const canUnlinkSelectedDevice = !selectedDevice.isOnline || !selectedDevice.isAvailable;
    const baseConnectionState = selectedDevice.isAvailable
      ? (selectedDevice.isOnline ? "Online" : "Offline")
      : "Unavailable";
    const bluetoothState = selectedDevice.isBluetoothOnline ? " • Bluetooth online" : "";
    const connectionState = `${baseConnectionState}${bluetoothState}`;
    const syncState = selectedDevice.isSynced ? "Synced" : "Not Synced";
    const cpuValue = selectedDevice.type === "Android"
      ? "Not available"
      : `${selectedDevice.cpuLoadPercent ?? 0}%`;
    const uploadValue = selectedDevice.wifiUploadSpeed && selectedDevice.wifiUploadSpeed !== "0 Mbps"
      ? selectedDevice.wifiUploadSpeed
      : "Not available";
    const downloadValue = selectedDevice.wifiDownloadSpeed && selectedDevice.wifiDownloadSpeed !== "0 Mbps"
      ? selectedDevice.wifiDownloadSpeed
      : "Not available";

    return (
      <section className="panel-stack">
        <div className="panel">
          <button className="text-btn" onClick={onCloseDeviceDetail} type="button">
            ← Back to devices
          </button>
          <h3>{selectedDevice.name}</h3>
          <p className="muted">{selectedDevice.type} • {selectedDevice.ipAddress}</p>
          <p className="section-label">Device details</p>

          <div className="device-detail-grid">
            <article className="row-card static">
              <span className="muted">Connection</span>
              <strong>{connectionState} • {syncState}</strong>
            </article>
            <article className="row-card static">
              <span className="muted">CPU</span>
              <strong>{cpuValue}</strong>
            </article>
            <article className="row-card static">
              <span className="muted">RAM</span>
              <strong>{formatRam(selectedDevice)}</strong>
            </article>
            <article className="row-card static">
              <span className="muted">Battery</span>
              <strong>{formatBattery(selectedDevice)}</strong>
            </article>
            <article className="row-card static">
              <span className="muted">Upload Speed</span>
              <strong>{uploadValue}</strong>
            </article>
            <article className="row-card static">
              <span className="muted">Download Speed</span>
              <strong>{downloadValue}</strong>
            </article>
          </div>

          <p className="muted">Last seen {new Date(selectedDevice.lastSeen).toLocaleString()}</p>

          <p className="section-label">Clipboard History</p>
          {selectedDevice.type === "Android" && (
            <p className="muted">
              Clipboard history for Android targets is not available due OS-level clipboard access restrictions.
            </p>
          )}

          {selectedDevice.type === "Linux" && !selectedDevice.isOnline && (
            <p className="muted">Target device must be online to fetch clipboard history.</p>
          )}

          {selectedDevice.type === "Linux" && selectedDevice.isOnline && (
            <>
              <button className="ghost" onClick={onRefreshClipboardHistory} type="button" disabled={clipboardHistoryLoading}>
                {clipboardHistoryLoading ? "Refreshing…" : "Refresh Clipboard History"}
              </button>

              <p className="muted">
                Shows up to 20 recent text entries when history providers are available; otherwise falls back to current clipboard text.
              </p>

              {clipboardHistoryMessage && <p className="muted">{clipboardHistoryMessage}</p>}

              <div className="list compact">
                {clipboardHistory.map((entry, index) => (
                  <article className="row-card static" key={`${index}-${entry.slice(0, 24)}`}>
                    <span className="clipboard-entry">{entry}</span>
                    <button onClick={() => onCopyClipboardHistoryEntry(entry)} type="button">Copy</button>
                  </article>
                ))}
              </div>
            </>
          )}

          <button onClick={() => onOpenChat(selectedDevice.id)} type="button">
            Open Chat
          </button>

          {canUnlinkSelectedDevice && (
            <button className="ghost" onClick={() => onUnlinkDevice(selectedDevice.id)} type="button">
              Unlink Device
            </button>
          )}
        </div>
      </section>
    );
  }

  return (
    <section className="panel-stack">
      <div className="panel">
        <p className="section-label">Online — {onlineDevices.length}</p>
        {onlineDevices.length === 0 && <p className="muted">No devices online.</p>}
        <div className="list">
          {onlineDevices.map((device) => (
            <button className="row-card" key={device.id} onClick={() => onSelectDevice(device.id)} type="button">
              <div className="item-head">
                <span className={`status-dot ${device.isOnline ? "online" : "offline"}`} />
                <strong>{device.name}</strong>
              </div>
              <span className="muted">{device.type} • {device.ipAddress}</span>
              {device.isBluetoothOnline && <span className="muted">Bluetooth online</span>}
              <span className="muted">
                CPU {device.cpuLoadPercent ?? 0}% · ↑ {device.wifiUploadSpeed} · ↓ {device.wifiDownloadSpeed}
              </span>
              <span className="muted">RAM {formatRam(device)} · Battery {formatBattery(device)}</span>
            </button>
          ))}
        </div>
      </div>
      <div className="panel">
        <p className="section-label">Offline — {offlineDevices.length}</p>
        {offlineDevices.length === 0 && <p className="muted">No offline devices.</p>}
        <div className="list">
          {offlineDevices.map((device) => (
            <article className="row-card static" key={device.id}>
              <div className="item-head">
                <span className={`status-dot ${device.isOnline ? "online" : "offline"}`} />
                <strong>{device.name}</strong>
              </div>
              <span className="muted">{device.type} • Last seen {new Date(device.lastSeen).toLocaleString()}</span>
              {device.isBluetoothOnline && <span className="muted">Bluetooth online</span>}
              <span className="muted">
                CPU {device.cpuLoadPercent ?? 0}% · ↑ {device.wifiUploadSpeed} · ↓ {device.wifiDownloadSpeed}
              </span>
              <span className="muted">RAM {formatRam(device)} · Battery {formatBattery(device)}</span>
              <div className="split-row">
                <button onClick={() => onSelectDevice(device.id)} type="button">
                  View Details
                </button>
                <button className="ghost" onClick={() => onUnlinkDevice(device.id)} type="button">
                  Unlink Device
                </button>
              </div>
            </article>
          ))}
        </div>
        <div className="split-row">
          <input
            aria-label="Device name"
            placeholder="Device name"
            value={deviceAlias}
            onChange={(event) => onDeviceAliasChange(event.target.value)}
          />
          {currentDevice ? (
            <button onClick={onUnlinkCurrentDevice} type="button">
              Unlink This Device
            </button>
          ) : (
            <button onClick={onLinkCurrentDevice} type="button">
              Link This Device
            </button>
          )}
        </div>
      </div>
    </section>
  );
}
