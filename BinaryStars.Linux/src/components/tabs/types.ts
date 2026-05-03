import type { ChatMessage } from "../../types";

export type Tab = "Devices" | "Files" | "Notes" | "Messaging" | "Notifications" | "Map" | "Actions" | "Settings" | "Logs" | "Bluetooth";

export const tabs: Tab[] = ["Devices", "Files", "Notes", "Messaging", "Notifications", "Map", "Actions", "Settings", "Logs", "Bluetooth"];

export interface ChatSummary {
  deviceId: string;
  lastMessage: ChatMessage;
  name: string;
}
