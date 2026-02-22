import type { AccountProfile, Device } from "../../types";

type Props = {
  profile: AccountProfile | null;
  devices: Device[];
  isDark: boolean;
  onSetIsDark: (value: boolean) => void;
  onSignOut: () => void;
};

export default function SettingsTab({ profile, devices, isDark, onSetIsDark, onSignOut }: Props) {
  return (
    <section className="panel-grid">
      <div className="panel">
        <p className="section-label">Account</p>
        <p>
          <strong>{profile?.username ?? "Unknown"}</strong>
        </p>
        <p className="muted">{profile?.email ?? "Unknown"}</p>
        <p className="muted">Plan: <span className="plan-badge">{profile?.role ?? "Unknown"}</span></p>
        <button className="ghost" type="button">Upgrade</button>
        <button onClick={onSignOut} type="button">Sign out</button>
      </div>
      <div className="panel">
        <p className="section-label">Appearance</p>
        <label className="inline">
          Dark mode
          <input checked={isDark} onChange={(event) => onSetIsDark(event.target.checked)} type="checkbox" />
        </label>
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
