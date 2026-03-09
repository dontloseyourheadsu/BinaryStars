import type { Device } from "../../types";

type Props = {
  linuxDevices: Device[];
  selectedDeviceId: string;
  selectedDevice: Device | null;
  onSelectDevice: (deviceId: string) => void;
  onSendBlockScreen: () => void;
  busy: boolean;
};

export default function ActionsTab({
  linuxDevices,
  selectedDeviceId,
  selectedDevice,
  onSelectDevice,
  onSendBlockScreen,
  busy,
}: Props) {
  return (
    <section className="panel-grid">
      <div className="panel">
        <h3>Linux Devices</h3>
        {linuxDevices.length === 0 && <p className="empty-state">No Linux devices linked.</p>}
        <div className="list compact">
          {linuxDevices.map((device) => (
            <button
              className="row-card"
              key={device.id}
              onClick={() => onSelectDevice(device.id)}
              type="button"
            >
              <div className="item-head">
                <span className={`status-dot ${device.isOnline ? "online" : "offline"}`} />
                <strong>{device.name}</strong>
              </div>
              <span className="muted">{device.id}</span>
              <span className="muted">{device.isOnline ? "Online" : "Offline"}</span>
              {selectedDeviceId === device.id && <span className="muted">Selected</span>}
            </button>
          ))}
        </div>
      </div>

      <div className="panel">
        <h3>Actions</h3>
        {!selectedDevice && <p className="empty-state">Select a Linux device to manage actions.</p>}
        {selectedDevice && (
          <>
            <p className="section-label">Target</p>
            <p className="muted">
              {selectedDevice.name} • {selectedDevice.id}
            </p>
            <p className="muted">
              {selectedDevice.isOnline
                ? "Device is online and can receive action commands."
                : "Device is offline. Actions are disabled until it reconnects."}
            </p>

            <button
              onClick={onSendBlockScreen}
              type="button"
              disabled={!selectedDevice.isOnline || busy}
            >
              Block Screen
            </button>
            <p className="muted">
              Supports GNOME/KDE/GTK-oriented desktops with graceful fallback when lock APIs are unavailable.
            </p>
          </>
        )}
      </div>
    </section>
  );
}
