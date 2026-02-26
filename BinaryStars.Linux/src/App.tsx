import { ChangeEvent, useEffect, useMemo, useRef, useState } from "react";
import { Icon } from "leaflet";
import markerIcon2x from "leaflet/dist/images/marker-icon-2x.png";
import markerIcon from "leaflet/dist/images/marker-icon.png";
import markerShadow from "leaflet/dist/images/marker-shadow.png";
import "./App.css";
import { api, tokenStore } from "./api";
import { getDeviceId, getDeviceName, setDeviceName } from "./device";
import { getBluetoothConnectedDeviceNames, getLocalDeviceInfo, isWifiConnected } from "./tauriDeviceInfo";
import { cacheStore, settingsStore } from "./storage";
import type { ThemeMode } from "./storage";
import type {
  AccountProfile,
  ChatMessage,
  DevicePresenceEvent,
  Device,
  FileTransfer,
  LocationPoint,
  MessageDto,
  Note,
} from "./types";
import AuthView from "./components/Auth/AuthView";
import Sidebar from "./components/UI/Sidebar";
import { toWsUrl, upsertMessage } from "./utils/helpers";
import DevicesTab from "./components/tabs/DevicesTab";
import FilesTab from "./components/tabs/FilesTab";
import NotesTab from "./components/tabs/NotesTab";
import MessagingTab from "./components/tabs/MessagingTab";
import MapTab from "./components/tabs/MapTab";
import SettingsTab from "./components/tabs/SettingsTab";
import { Tab, tabs } from "./components/tabs/types";
import { usePresenceHeartbeat } from "./hooks/usePresenceHeartbeat";

type EffectiveTheme = "light" | "dark";

function getSystemTheme(): EffectiveTheme {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
    return "light";
  }
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

async function applyNativeWindowTheme(theme: EffectiveTheme): Promise<void> {
  try {
    const { getCurrentWindow } = await import("@tauri-apps/api/window");
    await getCurrentWindow().setTheme(theme);
  } catch {
    // no-op when running in browser or on unsupported platforms
  }
}

function App() {
  // Fix Leaflet default icon paths for Vite
  delete (Icon.Default as unknown as { prototype?: { _getIconUrl?: unknown } }).prototype?._getIconUrl;
  Icon.Default.mergeOptions({
    iconRetinaUrl: markerIcon2x,
    iconUrl: markerIcon,
    shadowUrl: markerShadow,
  });

  const [isAuthed, setIsAuthed] = useState(Boolean(tokenStore.getToken()));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [online, setOnline] = useState(typeof navigator !== "undefined" ? navigator.onLine : true);
  const [activeTab, setActiveTab] = useState<Tab>("Devices");
  const [drawerOpen, setDrawerOpen] = useState(false);

  const [devices, setDevices] = useState<Device[]>(cacheStore.getDevices());
  const [transfers, setTransfers] = useState<FileTransfer[]>(cacheStore.getTransfers());
  const [notes, setNotes] = useState<Note[]>(cacheStore.getNotes());
  const [messages, setMessages] = useState<ChatMessage[]>(cacheStore.getMessages());
  const [profile, setProfile] = useState<AccountProfile | null>(cacheStore.getProfile());
  const [history, setHistory] = useState<LocationPoint[]>([]);
  const [selectedMapDeviceId, setSelectedMapDeviceId] = useState("");
  const [selectedChatDeviceId, setSelectedChatDeviceId] = useState("");
  const [selectedDeviceDetailId, setSelectedDeviceDetailId] = useState("");
  const [newMessage, setNewMessage] = useState("");
  const [noteName, setNoteName] = useState("");
  const [noteContent, setNoteContent] = useState("");
  const [noteType, setNoteType] = useState<"Plaintext" | "Markdown">("Plaintext");
  const [editingNoteId, setEditingNoteId] = useState<string | null>(null);
  const [deviceAlias, setDeviceAlias] = useState(getDeviceName());
  const [themeMode, setThemeMode] = useState<ThemeMode>(settingsStore.getThemeMode("system"));
  const [systemTheme, setSystemTheme] = useState<EffectiveTheme>(getSystemTheme());
  const [locationEnabled, setLocationEnabled] = useState(settingsStore.getLocationEnabled(false));
  const [locationMinutes, setLocationMinutes] = useState(settingsStore.getLocationMinutes(15));
  const [wifiAvailable, setWifiAvailable] = useState(true);
  const [localMemoryLoadPercent, setLocalMemoryLoadPercent] = useState<number | null>(null);
  const [mapDetailOpen, setMapDetailOpen] = useState(false);
  const [bluetoothConnectedNames, setBluetoothConnectedNames] = useState<string[]>([]);

  const wsRef = useRef<WebSocket | null>(null);
  const filePickerRef = useRef<HTMLInputElement | null>(null);
  const noteContentRef = useRef<HTMLTextAreaElement | null>(null);
  const myDeviceId = getDeviceId();

  const resolvedTheme: EffectiveTheme = themeMode === "system" ? systemTheme : themeMode;

  useEffect(() => {
    if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
      return;
    }

    const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
    const onThemeChange = (event: MediaQueryListEvent): void => {
      setSystemTheme(event.matches ? "dark" : "light");
    };

    setSystemTheme(mediaQuery.matches ? "dark" : "light");
    mediaQuery.addEventListener("change", onThemeChange);
    return () => {
      mediaQuery.removeEventListener("change", onThemeChange);
    };
  }, []);

  useEffect(() => {
    document.documentElement.dataset.theme = resolvedTheme;
    document.documentElement.style.colorScheme = resolvedTheme;
    settingsStore.setThemeMode(themeMode);
    void applyNativeWindowTheme(resolvedTheme);
  }, [resolvedTheme, themeMode]);

  useEffect(() => {
    const onOnline = () => setOnline(true);
    const onOffline = () => setOnline(false);
    window.addEventListener("online", onOnline);
    window.addEventListener("offline", onOffline);
    return () => {
      window.removeEventListener("online", onOnline);
      window.removeEventListener("offline", onOffline);
    };
  }, []);

  const selectedMapDevice = useMemo(
    () => devices.find((entry) => entry.id === selectedMapDeviceId) ?? null,
    [devices, selectedMapDeviceId],
  );

  const latestPoint = history[0] ?? null;

  const chatDevice = useMemo(
    () => devices.find((entry) => entry.id === selectedChatDeviceId) ?? null,
    [devices, selectedChatDeviceId],
  );

  const chatMessages = useMemo(
    () => messages.filter((entry) => entry.deviceId === selectedChatDeviceId),
    [messages, selectedChatDeviceId],
  );

  const selectedDeviceDetail = useMemo(
    () => devices.find((entry) => entry.id === selectedDeviceDetailId) ?? null,
    [devices, selectedDeviceDetailId],
  );

  const chatSummaries = useMemo(() => {
    const map = new Map<string, ChatMessage>();
    messages.forEach((message) => {
      const prev = map.get(message.deviceId);
      if (!prev || prev.sentAt < message.sentAt) {
        map.set(message.deviceId, message);
      }
    });
    return Array.from(map.entries())
      .map(([deviceId, lastMessage]) => ({
        deviceId,
        lastMessage,
        name: devices.find((entry) => entry.id === deviceId)?.name ?? deviceId,
      }))
      .sort((left, right) => right.lastMessage.sentAt - left.lastMessage.sentAt);
  }, [devices, messages]);

  useEffect(() => {
    cacheStore.setMessages(messages);
  }, [messages]);

  useEffect(() => {
    cacheStore.setNotes(notes);
  }, [notes]);

  useEffect(() => {
    cacheStore.setTransfers(transfers);
  }, [transfers]);

  useEffect(() => {
    cacheStore.setDevices(devices);
  }, [devices]);

  useEffect(() => {
    cacheStore.setProfile(profile);
  }, [profile]);

  const refreshProfile = async (): Promise<void> => {
    const next = await api.getProfile();
    setProfile(next);
  };

  const refreshDevices = async (): Promise<void> => {
    const next = await api.getDevices();
    const bluetoothSet = new Set(bluetoothConnectedNames);
    setDevices(next.map((device) => ({
      ...device,
      isBluetoothOnline: bluetoothSet.has(device.name),
    })));
    if (!selectedMapDeviceId && next.length > 0) {
      setSelectedMapDeviceId(next[0].id);
    }
  };

  const refreshNotes = async (): Promise<void> => {
    const next = await api.getNotes();
    setNotes(next);
  };

  const refreshTransfers = async (): Promise<void> => {
    const next = await api.getTransfers();
    setTransfers(next);
  };

  const toLocalLocationPoint = (payload: {
    deviceId: string;
    latitude: number;
    longitude: number;
    recordedAt: string;
  }) => ({
    id: crypto.randomUUID(),
    deviceId: payload.deviceId,
    title: "Local snapshot",
    recordedAt: payload.recordedAt,
    latitude: payload.latitude,
    longitude: payload.longitude,
  });

  const mergeHistory = (remote: LocationPoint[], local: LocationPoint[]): LocationPoint[] => {
    return [...remote, ...local]
      .sort((left, right) => Date.parse(right.recordedAt) - Date.parse(left.recordedAt))
      .slice(0, 500);
  };

  const saveLocalLocationPoint = (payload: {
    deviceId: string;
    latitude: number;
    longitude: number;
    recordedAt: string;
  }): void => {
    cacheStore.addLocalLocationPoint(toLocalLocationPoint(payload));
  };

  const queuePendingLocationUpload = (payload: {
    deviceId: string;
    latitude: number;
    longitude: number;
    accuracyMeters: number | null;
    recordedAt: string;
  }): void => {
    cacheStore.addPendingLocationUpload({
      id: crypto.randomUUID(),
      ...payload,
    });
  };

  const flushPendingLocationUploads = async (deviceId: string): Promise<void> => {
    const pending = cacheStore.getPendingLocationUploads(deviceId);
    if (pending.length === 0) {
      return;
    }

    const sentIds: string[] = [];
    for (const item of pending) {
      try {
        await api.sendLocation({
          deviceId: item.deviceId,
          latitude: item.latitude,
          longitude: item.longitude,
          accuracyMeters: item.accuracyMeters,
          recordedAt: item.recordedAt,
        });
        sentIds.push(item.id);
      } catch {
        break;
      }
    }

    cacheStore.removePendingLocationUploads(deviceId, sentIds);
  };

  const getCurrentPosition = async (): Promise<GeolocationPosition> => {
    return new Promise<GeolocationPosition>((resolve, reject) => {
      navigator.geolocation.getCurrentPosition(resolve, reject, { enableHighAccuracy: true });
    });
  };

  const refreshMapHistory = async (deviceId: string): Promise<void> => {
    const local = cacheStore.getLocalLocationHistory(deviceId);
    if (!online) {
      setHistory(local);
      return;
    }

    try {
      const remote = await api.getLocationHistory(deviceId);
      if (deviceId === myDeviceId) {
        setHistory(mergeHistory(remote, local));
        return;
      }

      setHistory(remote);
    } catch {
      setHistory(local);
    }
  };

  const initSession = async (): Promise<void> => {
    const canUseNetwork = typeof navigator === "undefined" || navigator.onLine;
    if (!canUseNetwork) {
      setDevices(cacheStore.getDevices());
      setProfile(cacheStore.getProfile());
      setError("No connection available. Showing cached data.");
      setIsAuthed(true);
      return;
    }

    try {
      setBusy(true);
      await Promise.all([refreshProfile(), refreshDevices(), refreshNotes(), refreshTransfers()]);
      setError("");
      setIsAuthed(true);
    } catch (nextError) {
      const message = nextError instanceof Error ? nextError.message : "Failed to load account session";
      const isOfflineError =
        !online ||
        message.toLowerCase().includes("network") ||
        message.toLowerCase().includes("fetch") ||
        message.toLowerCase().includes("timeout");

      if (isOfflineError) {
        setDevices(cacheStore.getDevices());
        setProfile(cacheStore.getProfile());
        setError("No connection available. Showing cached data.");
        setIsAuthed(true);
      } else {
        tokenStore.clear();
        setIsAuthed(false);
        setError(message);
      }
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    if (!isAuthed) {
      return;
    }
    void initSession();
  }, [isAuthed]);

  useEffect(() => {
    if (!isAuthed || !online) {
      return;
    }

    void refreshDevices().catch(() => {
      setError("No connection available");
    });
  }, [isAuthed, online]);

  useEffect(() => {
    if (!isAuthed) {
      return;
    }

    const refreshBluetoothPresence = async (): Promise<void> => {
      const names = await getBluetoothConnectedDeviceNames();
      setBluetoothConnectedNames(names);
    };

    void refreshBluetoothPresence();
    const timer = window.setInterval(() => {
      void refreshBluetoothPresence();
    }, 12_000);

    return () => window.clearInterval(timer);
  }, [isAuthed]);

  useEffect(() => {
    if (!isAuthed) {
      return;
    }

    const refreshWifiState = async (): Promise<void> => {
      const connected = await isWifiConnected();
      setWifiAvailable(connected);
    };

    void refreshWifiState();
    const timer = window.setInterval(() => {
      void refreshWifiState();
    }, 12_000);

    return () => window.clearInterval(timer);
  }, [isAuthed]);

  useEffect(() => {
    const bluetoothSet = new Set(bluetoothConnectedNames);
    setDevices((prev) => prev.map((device) => ({
      ...device,
      isBluetoothOnline: bluetoothSet.has(device.name),
    })));
  }, [bluetoothConnectedNames]);

  useEffect(() => {
    if (!isAuthed || !online || !wifiAvailable) {
      return;
    }

    void flushPendingLocationUploads(myDeviceId);
  }, [isAuthed, online, wifiAvailable, myDeviceId]);

  useEffect(() => {
    if (!isAuthed) {
      return;
    }
    const token = tokenStore.getToken();
    if (!token) {
      return;
    }
    const socket = new WebSocket(toWsUrl(myDeviceId), [token]);
    wsRef.current = socket;

    socket.onmessage = (event) => {
      try {
        const envelope = JSON.parse(event.data as string) as { type: string; payload: unknown };
        const messageType = envelope.type.toLowerCase();
        if (messageType === "device_presence") {
          const payload = envelope.payload as DevicePresenceEvent;
          setDevices((prev) => prev.map((device) => {
            if (device.id !== payload.deviceId) {
              return device;
            }

            return {
              ...device,
              isOnline: payload.isOnline,
              lastSeen: payload.lastSeen,
            };
          }));
          return;
        }

        if (messageType !== "message") {
          return;
        }
        const payload = envelope.payload as MessageDto;
        const chatDeviceId = payload.senderDeviceId === myDeviceId ? payload.targetDeviceId : payload.senderDeviceId;
        const nextMessage: ChatMessage = {
          id: payload.id,
          deviceId: chatDeviceId,
          senderDeviceId: payload.senderDeviceId,
          body: payload.body,
          sentAt: Date.parse(payload.sentAt),
          isOutgoing: payload.senderDeviceId === myDeviceId,
        };
        setMessages((prev) => upsertMessage(prev, nextMessage));
      } catch {
        // ignore malformed socket payloads
      }
    };

    return () => {
      socket.close();
      wsRef.current = null;
    };
  }, [isAuthed, myDeviceId]);

  usePresenceHeartbeat(isAuthed, myDeviceId);

  useEffect(() => {
    if (!isAuthed || !locationEnabled) {
      return;
    }
    settingsStore.setLocationEnabled(locationEnabled);
    settingsStore.setLocationMinutes(locationMinutes);

    const run = async (): Promise<void> => {
      try {
        const position = await getCurrentPosition();
        const recordedAt = new Date().toISOString();
        const payload = {
          deviceId: myDeviceId,
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          accuracyMeters: position.coords.accuracy,
          recordedAt,
        };

        saveLocalLocationPoint(payload);

        if (!online) {
          queuePendingLocationUpload(payload);
          if (selectedMapDeviceId === myDeviceId) {
            setHistory(cacheStore.getLocalLocationHistory(myDeviceId));
          }
          return;
        }

        const wifiConnected = await isWifiConnected();
        setWifiAvailable(wifiConnected);
        if (!wifiConnected) {
          queuePendingLocationUpload(payload);
          if (selectedMapDeviceId === myDeviceId) {
            setHistory(cacheStore.getLocalLocationHistory(myDeviceId));
          }
          return;
        }

        await flushPendingLocationUploads(myDeviceId);
        await api.sendLocation(payload);

        if (selectedMapDeviceId === myDeviceId) {
          await refreshMapHistory(myDeviceId);
        }
      } catch {
        // noop
      }
    };

    void run();
    const timer = window.setInterval(run, locationMinutes * 60_000);
    return () => window.clearInterval(timer);
  }, [isAuthed, locationEnabled, locationMinutes, myDeviceId, online, selectedMapDeviceId]);

  useEffect(() => {
    if (!selectedMapDeviceId || !isAuthed) {
      return;
    }
    void refreshMapHistory(selectedMapDeviceId);
  }, [isAuthed, selectedMapDeviceId, online]);

  const requireOnline = (): boolean => {
    if (online) {
      return true;
    }
    setError("No connection available");
    return false;
  };

  useEffect(() => {
    if (!selectedMapDeviceId) {
      setMapDetailOpen(false);
    }
  }, [selectedMapDeviceId]);

  useEffect(() => {
    if (!selectedDeviceDetailId) {
      return;
    }
    const exists = devices.some((entry) => entry.id === selectedDeviceDetailId);
    if (!exists) {
      setSelectedDeviceDetailId("");
    }
  }, [devices, selectedDeviceDetailId]);

  const linkCurrentDevice = async (): Promise<void> => {
    if (!requireOnline()) {
      return;
    }

    const local = await getLocalDeviceInfo();
    const alias = deviceAlias.trim() || local.hostname;
    if (alias !== deviceAlias) {
      setDeviceAlias(alias);
      setDeviceName(alias);
    }

    await api.registerDevice({
      id: myDeviceId,
      name: alias,
      ipAddress: local.ip_address,
      ipv6Address: local.ipv6_address,
      publicKey: "local-linux-key",
      publicKeyAlgorithm: "RSA",
    });

    await api.updateTelemetry(myDeviceId, {
      batteryLevel: local.battery_level ?? 100,
      cpuLoadPercent: local.cpu_load_percent ?? 0,
      isOnline: true,
      isAvailable: true,
      isSynced: true,
      wifiUploadSpeed: local.wifi_upload_speed,
      wifiDownloadSpeed: local.wifi_download_speed,
    });

    setLocalMemoryLoadPercent(local.memory_load_percent ?? null);

    await refreshDevices();
  };

  const unlinkCurrentDevice = async (): Promise<void> => {
    if (!requireOnline()) {
      return;
    }

    await api.unlinkDevice(myDeviceId);
    await refreshDevices();
  };

  const sendFile = async (file: File, targetDeviceId: string): Promise<void> => {
    if (!requireOnline()) {
      return;
    }

    const created = await api.createTransfer({
      fileName: file.name,
      contentType: file.type || "application/octet-stream",
      sizeBytes: file.size,
      senderDeviceId: myDeviceId,
      targetDeviceId,
      encryptionEnvelope: JSON.stringify({ alg: "none" }),
    });
    await api.uploadTransfer(created.id, file);
    await refreshTransfers();
  };

  const onPickFile = async (event: ChangeEvent<HTMLInputElement>): Promise<void> => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    const targets = devices.filter((entry) => entry.id !== myDeviceId);
    if (targets.length === 0) {
      setError("No target device available");
      return;
    }
    const targetId = window.prompt(
      `Send to device ID (available: ${targets.map((entry) => entry.id).join(", ")})`,
      targets[0].id,
    );
    if (!targetId) {
      return;
    }
    try {
      setBusy(true);
      await sendFile(file, targetId);
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : "Failed to send file");
    } finally {
      setBusy(false);
      event.target.value = "";
    }
  };

  const downloadTransfer = async (transfer: FileTransfer): Promise<void> => {
    if (!requireOnline()) {
      return;
    }

    const blob = await api.downloadTransfer(transfer.id, myDeviceId);
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = transfer.fileName;
    anchor.click();
    URL.revokeObjectURL(url);
    await refreshTransfers();
  };

  const rejectTransfer = async (transfer: FileTransfer): Promise<void> => {
    if (!requireOnline()) {
      return;
    }

    await api.rejectTransfer(transfer.id, myDeviceId);
    await refreshTransfers();
  };

  const saveNote = async (): Promise<void> => {
    if (!requireOnline()) {
      return;
    }

    if (!noteName.trim()) {
      setError("Note name is required");
      return;
    }
    if (editingNoteId) {
      await api.updateNote(editingNoteId, {
        name: noteName.trim(),
        content: noteContent,
      });
    } else {
      await api.createNote({
        name: noteName.trim(),
        content: noteContent,
        contentType: noteType,
        deviceId: myDeviceId,
      });
    }
    setEditingNoteId(null);
    setNoteName("");
    setNoteContent("");
    await refreshNotes();
  };

  const openNote = (note: Note): void => {
    setEditingNoteId(note.id);
    setNoteName(note.name);
    setNoteContent(note.content);
    setNoteType(note.contentType);
  };

  const wrapSelection = (before: string, after = before) => {
    const el = noteContentRef.current;
    if (!el) return;
    const start = el.selectionStart ?? 0;
    const end = el.selectionEnd ?? 0;
    const selected = noteContent.slice(start, end);
    const next = noteContent.slice(0, start) + before + selected + after + noteContent.slice(end);
    setNoteContent(next);
    // restore selection inside the wrapped text
    requestAnimationFrame(() => {
      el.focus();
      el.setSelectionRange(start + before.length, start + before.length + selected.length);
    });
  };

  const insertAtSelection = (text: string) => {
    const el = noteContentRef.current;
    if (!el) return;
    const start = el.selectionStart ?? 0;
    const end = el.selectionEnd ?? 0;
    const next = noteContent.slice(0, start) + text + noteContent.slice(end);
    setNoteContent(next);
    requestAnimationFrame(() => {
      el.focus();
      const cursor = start + text.length;
      el.setSelectionRange(cursor, cursor);
    });
  };

  const deleteNote = async (noteId: string): Promise<void> => {
    if (!requireOnline()) {
      return;
    }

    await api.deleteNote(noteId);
    if (editingNoteId === noteId) {
      setEditingNoteId(null);
      setNoteName("");
      setNoteContent("");
    }
    await refreshNotes();
  };

  const openChat = (deviceId: string): void => {
    setSelectedChatDeviceId(deviceId);
    setActiveTab("Messaging");
  };

  const openDeviceDetail = (deviceId: string): void => {
    setSelectedDeviceDetailId(deviceId);
  };

  const sendChatMessage = async (): Promise<void> => {
    if (!selectedChatDeviceId || !newMessage.trim()) {
      return;
    }
    const request = {
      senderDeviceId: myDeviceId,
      targetDeviceId: selectedChatDeviceId,
      body: newMessage.trim(),
      sentAt: new Date().toISOString(),
    };
    setNewMessage("");

    const socket = wsRef.current;
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ type: "message", payload: request }));
      const localMessage: ChatMessage = {
        id: crypto.randomUUID(),
        deviceId: selectedChatDeviceId,
        senderDeviceId: myDeviceId,
        body: request.body,
        sentAt: Date.now(),
        isOutgoing: true,
      };
      setMessages((prev) => upsertMessage(prev, localMessage));
      return;
    }

    if (!requireOnline()) {
      return;
    }

    const sent = await api.sendMessage(request);
    const storedMessage: ChatMessage = {
      id: sent.id,
      deviceId: selectedChatDeviceId,
      senderDeviceId: sent.senderDeviceId,
      body: sent.body,
      sentAt: Date.parse(sent.sentAt),
      isOutgoing: true,
    };
    setMessages((prev) => upsertMessage(prev, storedMessage));
  };

  const clearCurrentChat = (): void => {
    if (!selectedChatDeviceId) {
      return;
    }
    setMessages((prev) => prev.filter((entry) => entry.deviceId !== selectedChatDeviceId));
  };

  const signOut = (): void => {
    tokenStore.clear();
    wsRef.current?.close();
    setIsAuthed(false);
    setProfile(null);
    setDevices([]);
    setSelectedChatDeviceId("");
    setSelectedDeviceDetailId("");
    setHistory([]);
    setDrawerOpen(false);
  };

  const currentDevice = devices.find((entry) => entry.id === myDeviceId) ?? null;

  useEffect(() => {
    if (!isAuthed || !currentDevice || !online) {
      return;
    }

    const runTelemetrySync = async (): Promise<void> => {
      try {
        const local = await getLocalDeviceInfo();
        await api.updateTelemetry(myDeviceId, {
          batteryLevel: local.battery_level ?? 100,
          cpuLoadPercent: local.cpu_load_percent ?? 0,
          isOnline: true,
          isAvailable: true,
          isSynced: true,
          wifiUploadSpeed: local.wifi_upload_speed,
          wifiDownloadSpeed: local.wifi_download_speed,
        });

        setLocalMemoryLoadPercent(local.memory_load_percent ?? null);

        setDevices((prev) => prev.map((device) => {
          if (device.id !== myDeviceId) {
            return device;
          }

          return {
            ...device,
            ipAddress: local.ip_address,
            batteryLevel: local.battery_level ?? device.batteryLevel,
            isOnline: true,
            isAvailable: true,
            isSynced: true,
            cpuLoadPercent: local.cpu_load_percent ?? device.cpuLoadPercent,
            wifiUploadSpeed: local.wifi_upload_speed,
            wifiDownloadSpeed: local.wifi_download_speed,
            lastSeen: new Date().toISOString(),
          };
        }));
      } catch {
        // ignore telemetry sync failures to avoid breaking the UI
      }
    };

    void runTelemetrySync();
    const timer = window.setInterval(() => {
      void runTelemetrySync();
    }, 30_000);

    return () => window.clearInterval(timer);
  }, [isAuthed, currentDevice, myDeviceId, online]);

  useEffect(() => {
    if (!isAuthed || devices.length === 0) {
      return;
    }
    const hasCurrentDevice = devices.some((entry) => entry.id === myDeviceId);
    if (!hasCurrentDevice) {
      const shouldLink = window.confirm("Do you want to link this device to your account?");
      if (shouldLink) {
        void linkCurrentDevice();
      }
    }
  }, [devices, isAuthed, myDeviceId]);

  useEffect(() => {
    if (!isAuthed || transfers.length === 0) {
      return;
    }
    const hasPendingIncoming = transfers.some((entry) => !entry.isSender && entry.status === "Available");
    if (hasPendingIncoming) {
      const openFiles = window.confirm("You have files ready to download. Open Files tab now?");
      if (openFiles) {
        setActiveTab("Files");
      }
    }
  }, [isAuthed, transfers]);

  if (!isAuthed) {
    return (
      <>
        {error && <div className="banner error">{error}</div>}
        <AuthView
          busy={busy}
          onLoggedIn={() => setIsAuthed(true)}
          setBusy={setBusy}
          setError={setError}
        />
      </>
    );
  }

  const onlineDevices = devices.filter((entry) => entry.isOnline || entry.isBluetoothOnline === true);
  const offlineDevices = devices.filter((entry) => !entry.isOnline && entry.isBluetoothOnline !== true);

  const formatBattery = (device: Device): string => {
    return device.batteryLevel >= 0 ? `${device.batteryLevel}%` : "Not available";
  };

  const formatRam = (device: Device): string => {
    if (device.id !== myDeviceId) {
      return "Not available";
    }

    return localMemoryLoadPercent != null ? `${localMemoryLoadPercent}%` : "Not available";
  };

  return (
    <div className="app-shell">
        {error && <div className="banner error">{error}</div>}
        <Sidebar
          tabs={tabs}
          activeTab={activeTab}
          onSelect={(t) => {
            setActiveTab(t as Tab);
            setDrawerOpen(false);
          }}
          drawerOpen={drawerOpen}
        />
        <section className="content">
          <header className="page-header">
            <button className="menu-btn" onClick={() => setDrawerOpen((value) => !value)} type="button">
              ☰
            </button>
            <h2>{activeTab}</h2>
            <div className="header-actions">
              <button disabled={busy} onClick={() => void initSession()} type="button">
                Refresh
              </button>
            </div>
          </header>

        {!online && (
          <section className="panel no-connection">
            <p className="no-connection-title">No connection available</p>
            <p className="muted">Using cached data where available. Online actions are temporarily disabled.</p>
            <button onClick={() => void initSession()} type="button">Retry</button>
          </section>
        )}

        {online && !wifiAvailable && locationEnabled && (
          <section className="panel no-connection">
            <p className="no-connection-title">Wi‑Fi not available</p>
            <p className="muted">Location updates are cached locally and will sync when Wi‑Fi returns.</p>
          </section>
        )}

        {activeTab === "Devices" && (
          <DevicesTab
            onlineDevices={onlineDevices}
            offlineDevices={offlineDevices}
            currentDevice={currentDevice}
            selectedDevice={selectedDeviceDetail}
            deviceAlias={deviceAlias}
            onDeviceAliasChange={(value) => {
              setDeviceAlias(value);
              setDeviceName(value);
            }}
            onSelectDevice={openDeviceDetail}
            onCloseDeviceDetail={() => setSelectedDeviceDetailId("")}
            onOpenChat={openChat}
            onLinkCurrentDevice={() => void linkCurrentDevice()}
            onUnlinkCurrentDevice={() => void unlinkCurrentDevice()}
            formatRam={formatRam}
            formatBattery={formatBattery}
          />
        )}

        {activeTab === "Files" && (
          <FilesTab
            transfers={transfers}
            filePickerRef={filePickerRef}
            onPickFile={(event) => void onPickFile(event)}
            onDownloadTransfer={(transfer) => void downloadTransfer(transfer)}
            onRejectTransfer={(transfer) => void rejectTransfer(transfer)}
          />
        )}

        {activeTab === "Notes" && (
          <NotesTab
            notes={notes}
            editingNoteId={editingNoteId}
            noteName={noteName}
            noteContent={noteContent}
            noteType={noteType}
            noteContentRef={noteContentRef}
            setNoteName={setNoteName}
            setNoteContent={setNoteContent}
            setNoteType={setNoteType}
            onOpenNote={openNote}
            onDeleteNote={(noteId) => void deleteNote(noteId)}
            onSaveNote={() => void saveNote()}
            onResetEditor={() => {
              setEditingNoteId(null);
              setNoteName("");
              setNoteContent("");
            }}
            onWrapSelection={wrapSelection}
            onInsertAtSelection={insertAtSelection}
          />
        )}

        {activeTab === "Messaging" && (
          <MessagingTab
            devices={devices}
            chatSummaries={chatSummaries}
            chatDevice={chatDevice}
            chatMessages={chatMessages}
            selectedChatDeviceId={selectedChatDeviceId}
            newMessage={newMessage}
            onSelectChat={setSelectedChatDeviceId}
            onSetNewMessage={setNewMessage}
            onClearCurrentChat={clearCurrentChat}
            onSendChatMessage={() => void sendChatMessage()}
          />
        )}

        {activeTab === "Map" && (
          <MapTab
            devices={devices}
            selectedMapDevice={selectedMapDevice}
            selectedMapDeviceId={selectedMapDeviceId}
            latestPoint={latestPoint}
            history={history}
            mapDetailOpen={mapDetailOpen}
            locationEnabled={locationEnabled}
            locationMinutes={locationMinutes}
            onSelectMapDevice={setSelectedMapDeviceId}
            onSetMapDetailOpen={setMapDetailOpen}
            onRefreshMapHistory={(deviceId) => {
              void refreshMapHistory(deviceId);
            }}
            onSetLocationEnabled={(enabled) => {
              setLocationEnabled(enabled);
              settingsStore.setLocationEnabled(enabled);
            }}
            onSetLocationMinutes={(minutes) => {
              setLocationMinutes(minutes);
              settingsStore.setLocationMinutes(minutes);
            }}
          />
        )}

        {activeTab === "Settings" && (
          <SettingsTab
            profile={profile}
            devices={devices}
            themeMode={themeMode}
            onSetThemeMode={setThemeMode}
            onSignOut={signOut}
          />
        )}
      </section>
    </div>
  );
}

export default App;
