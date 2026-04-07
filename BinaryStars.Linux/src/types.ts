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
  isBluetoothOnline?: boolean;
  isAvailable: boolean;
  isSynced: boolean;
  cpuLoadPercent: number | null;
  wifiUploadSpeed: string;
  wifiDownloadSpeed: string;
  lastSeen: string;
  hasPendingNotificationSync?: boolean;
}

export interface DeviceNotificationMessage {
  id: string;
  userId: string;
  senderDeviceId: string;
  targetDeviceId: string;
  title: string;
  body: string;
  createdAt: string;
}

export interface NotificationSchedule {
  id: string;
  sourceDeviceId: string;
  targetDeviceId: string;
  title: string;
  body: string;
  isEnabled: boolean;
  scheduledForUtc: string | null;
  repeatMinutes: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface NotificationsPullResponse {
  hasPendingNotificationSync: boolean;
  notifications: DeviceNotificationMessage[];
  schedules: NotificationSchedule[];
}

export interface DeviceActionCommand {
  id: string;
  userId: string;
  senderDeviceId: string;
  targetDeviceId: string;
  actionType: string;
  payloadJson?: string | null;
  correlationId?: string | null;
  createdAt: string;
}

export interface DeviceActionResultMessage {
  id: string;
  userId: string;
  senderDeviceId: string;
  targetDeviceId: string;
  actionType: string;
  status: string;
  payloadJson?: string | null;
  error?: string | null;
  correlationId?: string | null;
  createdAt: string;
}

export interface LaunchableAppItem {
  name: string;
  exec: string;
  icon?: string | null;
  categories?: string | null;
  noDisplay: boolean;
}

export interface RunningAppItem {
  mainPid: number;
  pid: number;
  name: string;
  exe: string;
  cpuUsage: number;
  memoryMb: number;
  commandLine: string;
  processCount: number;
  pids: number[];
  hasVisibleWindow: boolean;
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

export interface DevicePresenceEvent {
  id: string;
  userId: string;
  deviceId: string;
  isOnline: boolean;
  lastSeen: string;
  occurredAt: string;
}

export interface LocationUpdateEvent {
  id: string;
  userId: string;
  deviceId: string;
  latitude: number;
  longitude: number;
  accuracyMeters: number | null;
  recordedAt: string;
  occurredAt: string;
}