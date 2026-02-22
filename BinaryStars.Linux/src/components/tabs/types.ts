import type { ChatMessage } from "../../types";

export type Tab = "Devices" | "Files" | "Notes" | "Messaging" | "Map" | "Settings";

export const tabs: Tab[] = ["Devices", "Files", "Notes", "Messaging", "Map", "Settings"];

export interface ChatSummary {
  deviceId: string;
  lastMessage: ChatMessage;
  name: string;
}
