export type UserRole = "Disabled" | "Free" | "Premium";
export type DeviceType = "Linux" | "Android";
export type NoteType = "Plaintext" | "Markdown";
export type FileTransferStatus =
  | "Queued"
  | "Uploading"
  | "Available"
  | "Downloaded"
  | "Failed"
  | "Expired"
  | "Rejected";

export interface AuthResponse {
  tokenType: string;
  accessToken: string;
  expiresIn: number;
}

export interface AccountProfile {
  id: string;
  username: string;
  email: string;
  role: UserRole;
}

export interface Device {
  id: string;
  name: string;
  type: DeviceType;
  ipAddress: string;
  publicKey: string | null;
  publicKeyAlgorithm: string | null;
  batteryLevel: number;
  isOnline: boolean;
  isAvailable: boolean;
  isSynced: boolean;
  cpuLoadPercent: number | null;
  wifiUploadSpeed: string;
  wifiDownloadSpeed: string;
  lastSeen: string;
}

export interface Note {
  id: string;
  name: string;
  signedByDeviceId: string;
  signedByDeviceName: string | null;
  contentType: NoteType;
  content: string;
  createdAt: string;
  updatedAt: string;
}

export interface FileTransfer {
  id: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  senderDeviceId: string;
  targetDeviceId: string;
  status: FileTransferStatus;
  createdAt: string;
  expiresAt: string;
  isSender: boolean;
}

export interface SendMessageRequest {
  senderDeviceId: string;
  targetDeviceId: string;
  body: string;
  sentAt?: string;
}

export interface MessageDto {
  id: string;
  userId: string;
  senderDeviceId: string;
  targetDeviceId: string;
  body: string;
  sentAt: string;
}

export interface LocationPoint {
  id: string;
  title: string;
  recordedAt: string;
  latitude: number;
  longitude: number;
}

export interface ChatMessage {
  id: string;
  deviceId: string;
  senderDeviceId: string;
  body: string;
  sentAt: number;
  isOutgoing: boolean;
}

export interface MessagingEnvelope {
  type: string;
  payload: unknown;
}