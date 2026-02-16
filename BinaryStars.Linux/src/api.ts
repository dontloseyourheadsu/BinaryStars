import axios, { AxiosError } from "axios";
import type {
  AccountProfile,
  AuthResponse,
  Device,
  FileTransfer,
  LocationPoint,
  MessageDto,
  Note,
  SendMessageRequest,
} from "./types";

const ACCESS_TOKEN_KEY = "binarystars.token";
const ACCESS_TOKEN_EXPIRY_KEY = "binarystars.token.exp";
const BASE_URL =
  (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "http://localhost:5004/api";

const http = axios.create({
  baseURL: BASE_URL,
  timeout: 30_000,
});

export const tokenStore = {
  getToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  },
  setToken(token: string, expiresInSeconds: number): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, token);
    localStorage.setItem(
      ACCESS_TOKEN_EXPIRY_KEY,
      String(Date.now() + expiresInSeconds * 1000),
    );
  },
  clear(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(ACCESS_TOKEN_EXPIRY_KEY);
  },
};

http.interceptors.request.use((config) => {
  const token = tokenStore.getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => {
    const refreshedToken = response.headers["x-access-token"] as string | undefined;
    const refreshedExpiresRaw = response.headers["x-access-token-expiresin"] as string | undefined;
    const refreshedExpires = Number(refreshedExpiresRaw);
    if (refreshedToken && Number.isFinite(refreshedExpires) && refreshedExpires > 0) {
      tokenStore.setToken(refreshedToken, refreshedExpires);
    }
    return response;
  },
  (error: AxiosError) => Promise.reject(error),
);

export const api = {
  async login(email: string, password: string): Promise<AuthResponse> {
    const response = await http.post<AuthResponse>("/auth/login", { email, password });
    return response.data;
  },
  async register(username: string, email: string, password: string): Promise<AuthResponse> {
    const response = await http.post<AuthResponse>("/auth/register", {
      username,
      email,
      password,
    });
    return response.data;
  },
  async externalLogin(provider: "google" | "microsoft", token: string, username = ""): Promise<AuthResponse> {
    const response = await http.post<AuthResponse>("/auth/login/external", {
      provider,
      token,
      username,
    });
    return response.data;
  },
  async getProfile(): Promise<AccountProfile> {
    const response = await http.get<AccountProfile>("/accounts/me");
    return response.data;
  },
  async getDevices(): Promise<Device[]> {
    const response = await http.get<Device[]>("/devices");
    return response.data;
  },
  async registerDevice(payload: {
    id: string;
    name: string;
    ipAddress: string;
    ipv6Address: string;
    publicKey: string;
    publicKeyAlgorithm: string;
  }): Promise<Device> {
    const response = await http.post<Device>("/devices/register", payload);
    return response.data;
  },
  async unlinkDevice(deviceId: string): Promise<void> {
    await http.delete(`/devices/${encodeURIComponent(deviceId)}`);
  },
  async updateTelemetry(deviceId: string, payload: {
    batteryLevel: number;
    cpuLoadPercent: number;
    isOnline: boolean;
    isAvailable: boolean;
    isSynced: boolean;
    wifiUploadSpeed: string;
    wifiDownloadSpeed: string;
  }): Promise<void> {
    await http.put(`/devices/${encodeURIComponent(deviceId)}/telemetry`, payload);
  },
  async getNotes(): Promise<Note[]> {
    const response = await http.get<Note[]>("/notes");
    return response.data;
  },
  async createNote(payload: {
    name: string;
    deviceId: string;
    contentType: "Plaintext" | "Markdown";
    content: string;
  }): Promise<Note> {
    const response = await http.post<Note>("/notes", payload);
    return response.data;
  },
  async updateNote(noteId: string, payload: { name: string; content: string }): Promise<Note> {
    const response = await http.put<Note>(`/notes/${encodeURIComponent(noteId)}`, payload);
    return response.data;
  },
  async deleteNote(noteId: string): Promise<void> {
    await http.delete(`/notes/${encodeURIComponent(noteId)}`);
  },
  async getTransfers(): Promise<FileTransfer[]> {
    const response = await http.get<FileTransfer[]>("/files/transfers");
    return response.data;
  },
  async createTransfer(payload: {
    fileName: string;
    contentType: string;
    sizeBytes: number;
    senderDeviceId: string;
    targetDeviceId: string;
    encryptionEnvelope: string;
  }): Promise<{ id: string }> {
    const response = await http.post<{ id: string }>("/files/transfers", payload);
    return response.data;
  },
  async uploadTransfer(transferId: string, file: File): Promise<void> {
    await http.put(`/files/transfers/${encodeURIComponent(transferId)}/upload`, file, {
      headers: {
        "Content-Type": file.type || "application/octet-stream",
      },
      maxBodyLength: Infinity,
      maxContentLength: Infinity,
      timeout: 5 * 60_000,
    });
  },
  async downloadTransfer(transferId: string, deviceId: string): Promise<Blob> {
    const response = await http.get(
      `/files/transfers/${encodeURIComponent(transferId)}/download`,
      {
        params: { deviceId },
        responseType: "blob",
        timeout: 5 * 60_000,
      },
    );
    return response.data as Blob;
  },
  async rejectTransfer(transferId: string, deviceId: string): Promise<void> {
    await http.post(`/files/transfers/${encodeURIComponent(transferId)}/reject`, null, {
      params: { deviceId },
    });
  },
  async sendMessage(payload: SendMessageRequest): Promise<MessageDto> {
    const response = await http.post<MessageDto>("/messaging/send", payload);
    return response.data;
  },
  async sendLocation(payload: {
    deviceId: string;
    latitude: number;
    longitude: number;
    accuracyMeters: number | null;
    recordedAt: string;
  }): Promise<void> {
    await http.post("/locations", payload);
  },
  async getLocationHistory(deviceId: string): Promise<LocationPoint[]> {
    const response = await http.get<LocationPoint[]>("/locations/history", {
      params: { deviceId },
    });
    return response.data;
  },
};

export function getApiBaseUrl(): string {
  return BASE_URL;
}