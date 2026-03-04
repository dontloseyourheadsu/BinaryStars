import type {
  AccountProfile,
  ChatMessage,
  Device,
  DeviceNotificationMessage,
  FileTransfer,
  Note,
  NotificationSchedule,
} from "./types";

const NOTES_KEY = "binarystars.notes.cache";
const TRANSFERS_KEY = "binarystars.transfers.cache";
const MESSAGES_KEY = "binarystars.messages.cache";
const DEVICES_KEY = "binarystars.devices.cache";
const PROFILE_KEY = "binarystars.profile.cache";
const NOTIFICATION_HISTORY_KEY = "binarystars.notifications.history.cache";
const NOTIFICATION_SCHEDULES_KEY = "binarystars.notifications.schedules.cache";
const THEME_KEY = "binarystars.theme.mode";
const LEGACY_THEME_DARK_KEY = "binarystars.theme.dark";
const LOCATION_ENABLED_KEY = "binarystars.location.enabled";
const LOCATION_INTERVAL_KEY = "binarystars.location.minutes";
const LOCATION_LOCAL_HISTORY_KEY = "binarystars.location.localHistory";
const LOCATION_PENDING_UPLOADS_KEY = "binarystars.location.pendingUploads";

export interface LocalLocationPoint {
  id: string;
  deviceId: string;
  title: string;
  recordedAt: string;
  latitude: number;
  longitude: number;
}

export interface PendingLocationUpload {
  id: string;
  deviceId: string;
  latitude: number;
  longitude: number;
  accuracyMeters: number | null;
  recordedAt: string;
}

export interface LocalNotificationHistoryItem extends DeviceNotificationMessage {
  receivedAt: string;
}

function readJson<T>(key: string, fallback: T): T {
  const raw = localStorage.getItem(key);
  if (!raw) {
    return fallback;
  }
  try {
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

function writeJson<T>(key: string, value: T): void {
  localStorage.setItem(key, JSON.stringify(value));
}

export const cacheStore = {
  getNotes(): Note[] {
    return readJson<Note[]>(NOTES_KEY, []);
  },
  setNotes(notes: Note[]): void {
    writeJson(NOTES_KEY, notes);
  },
  getTransfers(): FileTransfer[] {
    return readJson<FileTransfer[]>(TRANSFERS_KEY, []);
  },
  setTransfers(transfers: FileTransfer[]): void {
    writeJson(TRANSFERS_KEY, transfers);
  },
  getMessages(): ChatMessage[] {
    return readJson<ChatMessage[]>(MESSAGES_KEY, []);
  },
  setMessages(messages: ChatMessage[]): void {
    writeJson(MESSAGES_KEY, messages);
  },
  getDevices(): Device[] {
    return readJson<Device[]>(DEVICES_KEY, []);
  },
  setDevices(devices: Device[]): void {
    writeJson(DEVICES_KEY, devices);
  },
  getProfile(): AccountProfile | null {
    return readJson<AccountProfile | null>(PROFILE_KEY, null);
  },
  setProfile(profile: AccountProfile | null): void {
    writeJson(PROFILE_KEY, profile);
  },
  getNotificationHistory(): LocalNotificationHistoryItem[] {
    return readJson<LocalNotificationHistoryItem[]>(NOTIFICATION_HISTORY_KEY, []);
  },
  setNotificationHistory(items: LocalNotificationHistoryItem[]): void {
    writeJson(NOTIFICATION_HISTORY_KEY, items);
  },
  getNotificationSchedules(): NotificationSchedule[] {
    return readJson<NotificationSchedule[]>(NOTIFICATION_SCHEDULES_KEY, []);
  },
  setNotificationSchedules(schedules: NotificationSchedule[]): void {
    writeJson(NOTIFICATION_SCHEDULES_KEY, schedules);
  },
  getLocalLocationHistory(deviceId: string): LocalLocationPoint[] {
    const all = readJson<LocalLocationPoint[]>(LOCATION_LOCAL_HISTORY_KEY, []);
    return all
      .filter((entry) => entry.deviceId === deviceId)
      .sort((left, right) => Date.parse(right.recordedAt) - Date.parse(left.recordedAt));
  },
  addLocalLocationPoint(point: LocalLocationPoint): void {
    const next = [point, ...readJson<LocalLocationPoint[]>(LOCATION_LOCAL_HISTORY_KEY, [])].slice(0, 2_000);
    writeJson(LOCATION_LOCAL_HISTORY_KEY, next);
  },
  getPendingLocationUploads(deviceId: string): PendingLocationUpload[] {
    const all = readJson<PendingLocationUpload[]>(LOCATION_PENDING_UPLOADS_KEY, []);
    return all.filter((entry) => entry.deviceId === deviceId);
  },
  addPendingLocationUpload(upload: PendingLocationUpload): void {
    const next = [...readJson<PendingLocationUpload[]>(LOCATION_PENDING_UPLOADS_KEY, []), upload].slice(-1_000);
    writeJson(LOCATION_PENDING_UPLOADS_KEY, next);
  },
  removePendingLocationUploads(deviceId: string, ids: string[]): void {
    if (ids.length === 0) {
      return;
    }

    const idSet = new Set(ids);
    const next = readJson<PendingLocationUpload[]>(LOCATION_PENDING_UPLOADS_KEY, [])
      .filter((entry) => entry.deviceId !== deviceId || !idSet.has(entry.id));
    writeJson(LOCATION_PENDING_UPLOADS_KEY, next);
  },
};

export const settingsStore = {
  getThemeMode(defaultValue: ThemeMode = "system"): ThemeMode {
    const raw = localStorage.getItem(THEME_KEY);
    if (raw === "light" || raw === "dark" || raw === "system") {
      return raw;
    }

    const legacy = localStorage.getItem(LEGACY_THEME_DARK_KEY);
    if (legacy === "1") {
      return "dark";
    }
    if (legacy === "0") {
      return "light";
    }

    return defaultValue;
  },
  setThemeMode(mode: ThemeMode): void {
    localStorage.setItem(THEME_KEY, mode);
  },
  getLocationEnabled(defaultValue: boolean): boolean {
    const raw = localStorage.getItem(LOCATION_ENABLED_KEY);
    return raw == null ? defaultValue : raw === "1";
  },
  setLocationEnabled(enabled: boolean): void {
    localStorage.setItem(LOCATION_ENABLED_KEY, enabled ? "1" : "0");
  },
  getLocationMinutes(defaultValue: number): number {
    const raw = localStorage.getItem(LOCATION_INTERVAL_KEY);
    const parsed = Number(raw);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : defaultValue;
  },
  setLocationMinutes(minutes: number): void {
    localStorage.setItem(LOCATION_INTERVAL_KEY, String(minutes));
  },
};

export type ThemeMode = "light" | "dark" | "system";