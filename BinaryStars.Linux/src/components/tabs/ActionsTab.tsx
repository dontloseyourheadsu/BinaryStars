import type { Device, LaunchableAppItem, RunningAppItem } from "../../types";

type Props = {
  linuxDevices: Device[];
  selectedDeviceId: string;
  selectedDevice: Device | null;
  onSelectDevice: (deviceId: string) => void;
  onSendBlockScreen: () => void;
  onSendShutdown: () => void;
  onSendReset: () => void;
  onFetchLaunchableApps: () => void;
  onFetchRunningApps: () => void;
  onOpenApp: (app: LaunchableAppItem) => void;
  onCloseApp: (app: RunningAppItem) => void;
  launchableApps: LaunchableAppItem[];
  runningApps: RunningAppItem[];
  actionMode: "base" | "open-app" | "close-app";
  onBackToActions: () => void;
  pendingActionType: string | null;
  pendingActionRequestId: string | null;
  pendingActionSecondsRemaining: number | null;
  pendingActionTimeoutMessage: string;
  busy: boolean;
};

export default function ActionsTab({
  linuxDevices,
  selectedDeviceId,
  selectedDevice,
  onSelectDevice,
  onSendBlockScreen,
  onSendShutdown,
  onSendReset,
  onFetchLaunchableApps,
  onFetchRunningApps,
  onOpenApp,
  onCloseApp,
  launchableApps,
  runningApps,
  actionMode,
  onBackToActions,
  pendingActionType,
  pendingActionRequestId,
  pendingActionSecondsRemaining,
  pendingActionTimeoutMessage,
  busy,
}: Props) {
  const pendingActionLabel = pendingActionType ? pendingActionType.split("_").join(" ") : "action";

  return (
    <section className="panel-grid">
      <div className="panel">
        <h3>Linux Devices</h3>
        {linuxDevices.length === 0 && <p className="empty-state">No online Linux devices available for actions.</p>}
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
            <button
              onClick={onSendShutdown}
              type="button"
              disabled={!selectedDevice.isOnline || busy}
            >
              Shut Down
            </button>
            <button
              onClick={onSendReset}
              type="button"
              disabled={!selectedDevice.isOnline || busy}
            >
              Reset
            </button>
            <button
              onClick={onFetchLaunchableApps}
              type="button"
              disabled={!selectedDevice.isOnline || busy}
            >
              List Installed Apps
            </button>
            <button
              onClick={onFetchRunningApps}
              type="button"
              disabled={!selectedDevice.isOnline || busy}
            >
              List Running Apps
            </button>
            <p className="muted">
              Supports GNOME/KDE/GTK-oriented desktops with graceful fallback when lock APIs are unavailable.
            </p>

            {pendingActionRequestId && (
              <div className="action-pending-box" role="status" aria-live="polite">
                <span className="action-pending-spinner" aria-hidden="true" />
                <span className="action-pending-text">
                  Waiting for {pendingActionLabel} result... {pendingActionSecondsRemaining ?? 0}s left
                </span>
              </div>
            )}

            {!pendingActionRequestId && pendingActionTimeoutMessage && (
              <p className="action-timeout-message" role="status" aria-live="polite">
                {pendingActionTimeoutMessage}
              </p>
            )}

            {actionMode !== "base" && (
              <>
                <button className="ghost" onClick={onBackToActions} type="button">
                  Back to Action Buttons
                </button>

                {actionMode === "open-app" && (
                  <div className="list compact">
                    {launchableApps.length === 0 && <p className="empty-state">No launchable apps available.</p>}
                    {launchableApps.map((app) => (
                      <article className="row-card static" key={`${app.name}-${app.exec}`}>
                        <strong>{app.name}</strong>
                        <span className="muted">{app.exec}</span>
                        <button onClick={() => onOpenApp(app)} type="button" disabled={busy}>Open</button>
                      </article>
                    ))}
                  </div>
                )}

                {actionMode === "close-app" && (
                  <div className="list compact">
                    {runningApps.length === 0 && <p className="empty-state">No running apps available.</p>}
                    {runningApps.map((app) => (
                      <article className="row-card static" key={`${app.pid}-${app.name}`}>
                        <strong>{app.name}</strong>
                        <span className="muted">PID {app.pid}</span>
                        {app.exe && <span className="muted">{app.exe}</span>}
                        <span className="muted">{app.commandLine}</span>
                        <button onClick={() => onCloseApp(app)} type="button" disabled={busy}>Close</button>
                      </article>
                    ))}
                  </div>
                )}
              </>
            )}
          </>
        )}
      </div>
    </section>
  );
}
