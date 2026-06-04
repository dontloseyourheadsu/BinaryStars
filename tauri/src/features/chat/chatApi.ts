import { invoke } from "@tauri-apps/api/core";

export interface BluetoothMessage {
  id: string;
  sender: string;
  content: string;
  isFile: boolean;
  fileName: string | null;
  base64Data: string | null;
  filePath: string | null;
  sentAt: number;
}

export async function sendBluetoothMessage(content: string): Promise<void> {
  return await invoke<void>("send_bluetooth_message", { content });
}

export async function sendBluetoothFile(name: string, base64Data: string): Promise<void> {
  return await invoke<void>("send_bluetooth_file", { name, base64Data });
}

export async function downloadBluetoothFile(msgId: string): Promise<string> {
  return await invoke<string>("download_bluetooth_file", { msgId });
}

export async function saveFileToCustomPath(msgId: string): Promise<string> {
  return await invoke<string>("save_file_to_custom_path", { msgId });
}

export async function getMessagesPaged(peerId: string, limit: number, offset: number): Promise<BluetoothMessage[]> {
  return await invoke<BluetoothMessage[]>("get_messages_paged", { peerId, limit, offset });
}

export interface RecentChat {
  peerId: string;
  lastMessage: string;
  lastMsgAt: number;
}

export async function getRecentChats(): Promise<RecentChat[]> {
  return await invoke<RecentChat[]>("get_recent_chats");
}

