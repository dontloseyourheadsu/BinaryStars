import type { ChatMessage, FileTransfer, Note } from "./types";

const NOTES_KEY = "binarystars.notes.cache";
const TRANSFERS_KEY = "binarystars.transfers.cache";
const MESSAGES_KEY = "binarystars.messages.cache";
const THEME_KEY = "binarystars.theme.dark";
const LOCATION_ENABLED_KEY = "binarystars.location.enabled";
const LOCATION_INTERVAL_KEY = "binarystars.location.minutes";

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
};

export const settingsStore = {
  getDarkMode(defaultValue: boolean): boolean {
    const raw = localStorage.getItem(THEME_KEY);
    return raw == null ? defaultValue : raw === "1";
  },
  setDarkMode(enabled: boolean): void {
    localStorage.setItem(THEME_KEY, enabled ? "1" : "0");
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