import type { AccountProfile, Device } from "../../types";
import type { ThemeMode } from "../../storage";

type Props = {
  profile: AccountProfile | null;
  devices: Device[];
  themeMode: ThemeMode;
  logFilePath?: string | null;
  onSetThemeMode: (value: ThemeMode) => void;
  onSignOut: () => void;
};

export default function SettingsTab({ profile, devices, themeMode, logFilePath, onSetThemeMode, onSignOut }: Props) {
  const copyLogPath = async (): Promise<void> => {
    if (!logFilePath) {
      return;
    }

    try {
      await navigator.clipboard.writeText(logFilePath);
    } catch {
      // no-op
    }
  };

  return (
    <section className="panel-grid">
      <div className="panel">
        <p className="section-label">Account</p>
        <p>
          <strong>{profile?.username ?? "Unknown"}</strong>
        </p>
        <p className="muted">{profile?.email ?? "Unknown"}</p>
        <button onClick={onSignOut} type="button">Sign out</button>
      </div>
      <div className="panel">
        <p className="section-label">Appearance</p>
        <label className="inline">
          Theme
          <select
            aria-label="Theme mode"
            value={themeMode}
            onChange={(event) => onSetThemeMode(event.target.value as ThemeMode)}
          >
            <option value="system">System</option>
            <option value="light">Light</option>
            <option value="dark">Dark</option>
          </select>
        </label>
        <p className="section-label">Debug Logs</p>
        <p className="muted">{logFilePath ?? "Log path unavailable in this runtime."}</p>
        <button type="button" onClick={() => void copyLogPath()} disabled={!logFilePath}>Copy Log Path</button>
        <h3>Connected devices ({devices.length})</h3>
        <div className="list compact">
          {devices.map((device) => (
            <article className="row-card static" key={device.id}>
              <strong>{device.name}</strong>
              <span className="muted">{device.id}</span>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
