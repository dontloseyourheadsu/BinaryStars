import type { Device } from "../../types";

type Props = {
  onlineDevices: Device[];
  offlineDevices: Device[];
  currentDevice: Device | null;
  deviceAlias: string;
  onDeviceAliasChange: (value: string) => void;
  onOpenChat: (deviceId: string) => void;
  onLinkCurrentDevice: () => void;
  onUnlinkCurrentDevice: () => void;
  formatRam: (device: Device) => string;
  formatBattery: (device: Device) => string;
};

export default function DevicesTab({
  onlineDevices,
  offlineDevices,
  currentDevice,
  deviceAlias,
  onDeviceAliasChange,
  onOpenChat,
  onLinkCurrentDevice,
  onUnlinkCurrentDevice,
  formatRam,
  formatBattery,
}: Props) {
  return (
    <section className="panel-stack">
      <div className="panel">
        <p className="section-label">Online — {onlineDevices.length}</p>
        {onlineDevices.length === 0 && <p className="muted">No devices online.</p>}
        <div className="list">
          {onlineDevices.map((device) => (
            <button className="row-card" key={device.id} onClick={() => onOpenChat(device.id)} type="button">
              <div className="item-head">
                <span className={`status-dot ${device.isOnline ? "online" : "offline"}`} />
                <strong>{device.name}</strong>
              </div>
              <span className="muted">{device.type} • {device.ipAddress}</span>
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
            <button className="row-card" key={device.id} onClick={() => onOpenChat(device.id)} type="button">
              <div className="item-head">
                <span className={`status-dot ${device.isOnline ? "online" : "offline"}`} />
                <strong>{device.name}</strong>
              </div>
              <span className="muted">{device.type} • Last seen {new Date(device.lastSeen).toLocaleString()}</span>
              <span className="muted">
                CPU {device.cpuLoadPercent ?? 0}% · ↑ {device.wifiUploadSpeed} · ↓ {device.wifiDownloadSpeed}
              </span>
              <span className="muted">RAM {formatRam(device)} · Battery {formatBattery(device)}</span>
            </button>
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
