import type { Device, NotificationSchedule } from "../../types";
import type { LocalNotificationHistoryItem } from "../../storage";

type Props = {
  devices: Device[];
  selectedTargetDeviceId: string;
  notificationTitle: string;
  notificationBody: string;
  scheduledForUtc: string;
  repeatMinutes: string;
  isScheduleEnabled: boolean;
  editingScheduleId: string | null;
  schedules: NotificationSchedule[];
  history: LocalNotificationHistoryItem[];
  onSelectTargetDevice: (deviceId: string) => void;
  onSetNotificationTitle: (value: string) => void;
  onSetNotificationBody: (value: string) => void;
  onSetScheduledForUtc: (value: string) => void;
  onSetRepeatMinutes: (value: string) => void;
  onSetScheduleEnabled: (value: boolean) => void;
  onSendNow: () => void;
  onSaveSchedule: () => void;
  onEditSchedule: (schedule: NotificationSchedule) => void;
  onDeleteSchedule: (scheduleId: string) => void;
  onResetEditor: () => void;
  onDeleteHistoryItem: (itemId: string) => void;
  onClearHistory: () => void;
  onRefresh: () => void;
};

export default function NotificationsTab({
  devices,
  selectedTargetDeviceId,
  notificationTitle,
  notificationBody,
  scheduledForUtc,
  repeatMinutes,
  isScheduleEnabled,
  editingScheduleId,
  schedules,
  history,
  onSelectTargetDevice,
  onSetNotificationTitle,
  onSetNotificationBody,
  onSetScheduledForUtc,
  onSetRepeatMinutes,
  onSetScheduleEnabled,
  onSendNow,
  onSaveSchedule,
  onEditSchedule,
  onDeleteSchedule,
  onResetEditor,
  onDeleteHistoryItem,
  onClearHistory,
  onRefresh,
}: Props) {
  const selectedDevice = devices.find((entry) => entry.id === selectedTargetDeviceId) ?? null;

  return (
    <section className="panel-grid">
      <div className="panel-stack">
        <div className="panel">
          <div className="split-row">
            <h3>{editingScheduleId ? "Update Schedule" : "Send Notification"}</h3>
            <button onClick={onRefresh} type="button">Refresh</button>
          </div>

          <label className="section-label" htmlFor="notification-target">Target device</label>
          <select
            id="notification-target"
            value={selectedTargetDeviceId}
            onChange={(event) => onSelectTargetDevice(event.target.value)}
          >
            {devices.map((device) => (
              <option key={device.id} value={device.id}>{device.name}</option>
            ))}
          </select>

          <label className="section-label" htmlFor="notification-title">Title</label>
          <input
            id="notification-title"
            value={notificationTitle}
            onChange={(event) => onSetNotificationTitle(event.target.value)}
            placeholder="Notification title"
          />

          <label className="section-label" htmlFor="notification-body">Body</label>
          <textarea
            id="notification-body"
            value={notificationBody}
            onChange={(event) => onSetNotificationBody(event.target.value)}
            placeholder="Write your notification"
            rows={4}
          />

          <div className="split-row">
            <button onClick={onSendNow} type="button">Send Now</button>
            <button className="ghost" onClick={onResetEditor} type="button">Reset</button>
          </div>

          <p className="section-label">Schedule</p>
          <div className="split-row">
            <label>
              <input
                type="checkbox"
                checked={isScheduleEnabled}
                onChange={(event) => onSetScheduleEnabled(event.target.checked)}
              />
              {" "}Enabled
            </label>
            <input
              type="datetime-local"
              title="Schedule for date and time"
              aria-label="Schedule for date and time"
              value={scheduledForUtc}
              onChange={(event) => onSetScheduledForUtc(event.target.value)}
            />
            <input
              type="number"
              min={1}
              step={1}
              value={repeatMinutes}
              onChange={(event) => onSetRepeatMinutes(event.target.value)}
              placeholder="Repeat every minutes"
            />
          </div>

          <button onClick={onSaveSchedule} type="button">
            {editingScheduleId ? "Update Schedule" : "Save Schedule"}
          </button>

          <p className="muted">
            Schedule uses either one-time date/time or repeat interval. Target: {selectedDevice?.name ?? "-"}.
          </p>
        </div>

        <div className="panel">
          <h3>Schedules</h3>
          {schedules.length === 0 && <p className="empty-state">No schedules yet for this device.</p>}
          <div className="list compact">
            {schedules.map((schedule) => (
              <article className="row-card static" key={schedule.id}>
                <strong>{schedule.title}</strong>
                <span className="muted">{schedule.body}</span>
                <span className="muted">
                  {schedule.repeatMinutes != null
                    ? `Repeats every ${schedule.repeatMinutes} min`
                    : schedule.scheduledForUtc
                      ? `Runs at ${new Date(schedule.scheduledForUtc).toLocaleString()}`
                      : "No trigger"}
                </span>
                <span className="muted">{schedule.isEnabled ? "Enabled" : "Disabled"}</span>
                <div className="split-row">
                  <button onClick={() => onEditSchedule(schedule)} type="button">Edit</button>
                  <button className="ghost" onClick={() => onDeleteSchedule(schedule.id)} type="button">Delete</button>
                </div>
              </article>
            ))}
          </div>
        </div>
      </div>

      <div className="panel">
        <div className="split-row">
          <h3>Received Notifications</h3>
          <button className="ghost" onClick={onClearHistory} type="button">Delete All</button>
        </div>
        {history.length === 0 && <p className="empty-state">No notifications received yet.</p>}
        <div className="list">
          {history.map((item) => (
            <article className="row-card static" key={item.id}>
              <strong>{item.title}</strong>
              <span className="muted">{item.body}</span>
              <span className="muted">
                From {item.senderDeviceId} • {new Date(item.createdAt).toLocaleString()}
              </span>
              <button className="ghost" onClick={() => onDeleteHistoryItem(item.id)} type="button">Delete</button>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
