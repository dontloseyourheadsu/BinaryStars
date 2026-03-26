import type { FileTransferStatus, ChatMessage } from "../types";
import { getApiBaseUrl } from "../api";

export function formatSize(sizeBytes: number): string {
  if (sizeBytes < 1024) {
    return `${sizeBytes} B`;
  }
  const units = ["KB", "MB", "GB"];
  let value = sizeBytes / 1024;
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }
  return `${value.toFixed(1)} ${units[unitIndex]}`;
}

export function statusLabel(status: FileTransferStatus, isSender: boolean): string {
  if (status === "Available") {
    return isSender ? "Waiting for download" : "Ready to download";
  }
  if (status === "Downloaded") {
    return isSender ? "Delivered" : "Downloaded";
  }
  return status;
}

export function toWsUrl(deviceId: string, accessToken?: string | null): string {
  const base = getApiBaseUrl().replace(/\/$/, "").replace(/\/api$/, "");
  const query = new URLSearchParams({ deviceId });
  if (accessToken) {
    query.set("access_token", accessToken);
  }

  if (base.startsWith("https://")) {
    return `${base.replace("https://", "wss://")}/ws/messaging?${query.toString()}`;
  }
  if (base.startsWith("http://")) {
    return `${base.replace("http://", "ws://")}/ws/messaging?${query.toString()}`;
  }
  return `ws://localhost:5004/ws/messaging?${query.toString()}`;
}

export function upsertMessage(list: ChatMessage[], next: ChatMessage): ChatMessage[] {
  const found = list.findIndex((entry) => entry.id === next.id);
  if (found >= 0) {
    const clone = [...list];
    clone[found] = next;
    return clone;
  }
  return [...list, next].sort((left, right) => left.sentAt - right.sentAt);
}
