import { ChangeEvent, useEffect, useMemo, useRef, useState } from "react";
import { Icon } from "leaflet";
import markerIcon2x from "leaflet/dist/images/marker-icon-2x.png";
import markerIcon from "leaflet/dist/images/marker-icon.png";
import markerShadow from "leaflet/dist/images/marker-shadow.png";
import "./App.css";
import { invoke } from "@tauri-apps/api/core";
import { api, tokenStore } from "./api";
import { getDeviceId, getDeviceName, setDeviceName } from "./device";
import {
  getBluetoothDevices,
  getElevationStatus,
  getNativeLocation,
  getLocalDeviceInfo,
  isTauriRuntime,
  isWifiConnected,
  performLocalAction,
  sendFileViaBluetooth,
} from "./tauriDeviceInfo";
import { cacheStore, settingsStore } from "./storage";
import type { LocalNotificationHistoryItem } from "./storage";
import type { ThemeMode } from "./storage";
import type {
  AccountProfile,
  ChatMessage,
  DeviceActionCommand,
  DeviceActionResultMessage,
  DevicePresenceEvent,
  Device,
  LaunchableAppItem,
  FileTransfer,
  LocationUpdateEvent,
  LocationPoint,
  MessageDto,
  NotificationSchedule,
  Note,
  RunningAppItem,
} from "./types";
import AuthView from "./components/Auth/AuthView";
import Sidebar from "./components/UI/Sidebar";
import { toWsUrl, upsertMessage } from "./utils/helpers";
import DevicesTab from "./components/tabs/DevicesTab";
import FilesTab from "./components/tabs/FilesTab";
import NotesTab from "./components/tabs/NotesTab";
import MessagingTab from "./components/tabs/MessagingTab";
import NotificationsTab from "./components/tabs/NotificationsTab";
import MapTab from "./components/tabs/MapTab";
import ActionsTab from "./components/tabs/ActionsTab";
import SettingsTab from "./components/tabs/SettingsTab";
import { Tab, tabs } from "./components/tabs/types";
import { usePresenceHeartbeat } from "./hooks/usePresenceHeartbeat";
import { getNativeLogFilePath, installInteractionLogging, logEvent } from "./logging";

type EffectiveTheme = "light" | "dark";
type SnackbarLevel = "error" | "info" | "success" | "warning";

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

  const [isAuthed, setIsAuthed] = useState(tokenStore.hasStoredSession());
  const [busy, setBusy] = useState(false);
  const [snackbar, setSnackbar] = useState<{ id: string; level: SnackbarLevel; message: string } | null>(null);
  const [online, setOnline] = useState(typeof navigator !== "undefined" ? navigator.onLine : true);
  const [activeTab, setActiveTab] = useState<Tab>("Devices");
  const [drawerOpen, setDrawerOpen] = useState(false);

  const [devices, setDevices] = useState<Device[]>(cacheStore.getDevices());
  const [transfers, setTransfers] = useState<FileTransfer[]>(cacheStore.getTransfers());
  const [notes, setNotes] = useState<Note[]>(cacheStore.getNotes());
  const [messages, setMessages] = useState<ChatMessage[]>(cacheStore.getMessages());
  const [notificationHistory, setNotificationHistory] = useState<LocalNotificationHistoryItem[]>(cacheStore.getNotificationHistory());
  const [notificationSchedules, setNotificationSchedules] = useState<NotificationSchedule[]>(cacheStore.getNotificationSchedules());
  const [profile, setProfile] = useState<AccountProfile | null>(cacheStore.getProfile());
  const [history, setHistory] = useState<LocationPoint[]>([]);
  const [mapFocusPoint, setMapFocusPoint] = useState<LocationPoint | null>(null);
  const [selectedMapDeviceId, setSelectedMapDeviceId] = useState("");
  const [selectedChatDeviceId, setSelectedChatDeviceId] = useState("");
  const [selectedFileTargetDeviceId, setSelectedFileTargetDeviceId] = useState("");
  const [selectedDeviceDetailId, setSelectedDeviceDetailId] = useState("");
  const [selectedActionDeviceId, setSelectedActionDeviceId] = useState("");
  const [actionMode, setActionMode] = useState<"base" | "open-app" | "close-app">("base");
  const [launchableApps, setLaunchableApps] = useState<LaunchableAppItem[]>([]);
  const [runningApps, setRunningApps] = useState<RunningAppItem[]>([]);
  const [pendingActionRequestId, setPendingActionRequestId] = useState<string | null>(null);
  const [pendingActionType, setPendingActionType] = useState<string | null>(null);
  const [pendingActionDeadlineAtMs, setPendingActionDeadlineAtMs] = useState<number | null>(null);
  const [pendingActionSecondsRemaining, setPendingActionSecondsRemaining] = useState<number | null>(null);
  const [pendingActionTimeoutMessage, setPendingActionTimeoutMessage] = useState("");
  const [clipboardHistory, setClipboardHistory] = useState<string[]>([]);
  const [clipboardHistoryLoading, setClipboardHistoryLoading] = useState(false);
  const [clipboardHistoryMessage, setClipboardHistoryMessage] = useState("");
  const [pendingClipboardRequestId, setPendingClipboardRequestId] = useState<string | null>(null);
  const [clipboardTargetDeviceId, setClipboardTargetDeviceId] = useState<string | null>(null);
  const [newMessage, setNewMessage] = useState("");
  const [notificationTitle, setNotificationTitle] = useState("");
  const [notificationBody, setNotificationBody] = useState("");
  const [notificationTargetDeviceId, setNotificationTargetDeviceId] = useState("");
  const [notificationScheduledFor, setNotificationScheduledFor] = useState("");
  const [notificationRepeatMinutes, setNotificationRepeatMinutes] = useState("");
  const [notificationScheduleEnabled, setNotificationScheduleEnabled] = useState(true);
  const [editingNotificationScheduleId, setEditingNotificationScheduleId] = useState<string | null>(null);
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
  const [isElevated, setIsElevated] = useState(false);
  const [localMemoryLoadPercent, setLocalMemoryLoadPercent] = useState<number | null>(null);
  const [mapDetailOpen, setMapDetailOpen] = useState(false);
  const [bluetoothConnectedNames, setBluetoothConnectedNames] = useState<string[]>([]);
  const [geoPermissionState, setGeoPermissionState] = useState<"granted" | "denied" | "prompt" | "unsupported" | "unknown" | "native">("unknown");
  const [lastGeoError, setLastGeoError] = useState("");
  const [lastLocationSampleAt, setLastLocationSampleAt] = useState<string | null>(null);
  const [lastLocationSource, setLastLocationSource] = useState<"native" | "geolocation" | null>(null);
  const [nativeLogFilePath, setNativeLogFilePath] = useState<string | null>(null);
  const isElevatedRuntime = isTauriRuntime() && isElevated;

  const normalizeNoteType = (value: unknown): "Plaintext" | "Markdown" => {
    if (typeof value === "string" && value.trim().toLowerCase() === "markdown") {
      return "Markdown";
    }

    if (typeof value === "number" && value === 1) {
      return "Markdown";
    }

    return "Plaintext";
  };

  const wsRef = useRef<WebSocket | null>(null);
  const selectedMapDeviceIdRef = useRef(selectedMapDeviceId);
  const filePickerRef = useRef<HTMLInputElement | null>(null);
  const noteContentRef = useRef<HTMLTextAreaElement | null>(null);
  const clipboardAutoFetchKeyRef = useRef<string>("");
  const myDeviceId = getDeviceId();

  const getActionTimeoutMs = (actionType: string): number => {
    if (actionType === "list_installed_apps" || actionType === "launch_app") {
      return 120_000;
    }
    return 30_000;
  };

  const formatActionLabel = (actionType: string | null): string => {
    if (!actionType) {
      return "Action";
    }

    return actionType
      .split("_")
      .map((part) => (part.length > 0 ? `${part[0].toUpperCase()}${part.slice(1)}` : part))
      .join(" ");
  };

  const clearPendingAction = (): void => {
    setPendingActionRequestId(null);
    setPendingActionType(null);
    setPendingActionDeadlineAtMs(null);
    setPendingActionSecondsRemaining(null);
  };

  const startPendingAction = (requestId: string, actionType: string): void => {
    const timeoutMs = getActionTimeoutMs(actionType);
    setPendingActionTimeoutMessage("");
    setPendingActionRequestId(requestId);
    setPendingActionType(actionType);
    setPendingActionDeadlineAtMs(Date.now() + timeoutMs);
    setPendingActionSecondsRemaining(Math.ceil(timeoutMs / 1000));
  };

  useEffect(() => {
    selectedMapDeviceIdRef.current = selectedMapDeviceId;
  }, [selectedMapDeviceId]);

  useEffect(() => {
    if (!pendingActionRequestId || !pendingActionDeadlineAtMs || !pendingActionType) {
      return;
    }

    const timeoutMs = getActionTimeoutMs(pendingActionType);
    const timer = window.setInterval(() => {
      const remainingMs = pendingActionDeadlineAtMs - Date.now();
      const remainingSeconds = Math.max(0, Math.ceil(remainingMs / 1000));
      setPendingActionSecondsRemaining(remainingSeconds);

      if (remainingMs <= 0) {
        const actionLabel = formatActionLabel(pendingActionType);
        clearPendingAction();
        setPendingActionTimeoutMessage(`${actionLabel} timed out after ${Math.ceil(timeoutMs / 1000)}s. Please try again.`);
        setError(`${actionLabel} timed out`);
      }
    }, 250);

    return () => window.clearInterval(timer);
  }, [pendingActionRequestId, pendingActionDeadlineAtMs, pendingActionType]);

  const handleTabSelect = (tab: Tab): void => {
    logEvent("info", "ui.navigation", "tab-selected", { tab });
    setActiveTab(tab);
  };

  useEffect(() => {
    installInteractionLogging();
    logEvent("info", "ui.lifecycle", "app-mounted", { runtime: isTauriRuntime() ? "tauri" : "browser" });

    void (async () => {
      const nativeLogPath = await getNativeLogFilePath();
      if (nativeLogPath) {
        setNativeLogFilePath(nativeLogPath);
        logEvent("info", "ui.logging", "native-log-path-resolved", { path: nativeLogPath });
      }
    })();
  }, []);

  const isConnectionIssueMessage = (message: string): boolean => {
    const normalized = message.trim().toLowerCase();
    if (!normalized) {
      return false;
    }

    return (
      normalized.includes("no connection available")
      || normalized.includes("showing cached data")
      || normalized.includes("network error")
      || normalized.includes("failed to fetch")
      || normalized.includes("timeout")
      || normalized.includes("offline")
    );
  };

  const showSnackbar = (level: SnackbarLevel, message: string): void => {
    const normalized = message.trim();
    if (!normalized) {
      setSnackbar(null);
      return;
    }

    setSnackbar({
      id: crypto.randomUUID(),
      level,
      message: normalized,
    });
  };

  const setError = (value: string): void => {
    if (!value) {
      setSnackbar(null);
      return;
    }

    if (isConnectionIssueMessage(value)) {
      // Connection state is already represented by the dedicated offline panel.
      setSnackbar(null);
      return;
    }

    showSnackbar("error", value);
  };

  const setInfo = (value: string): void => {
    showSnackbar("info", value);
  };

  const setSuccess = (value: string): void => {
    showSnackbar("success", value);
  };

  useEffect(() => {
    if (!snackbar) {
      return;
    }

    const timer = window.setTimeout(() => {
      setSnackbar(null);
    }, 6500);

    return () => {
      window.clearTimeout(timer);
    };
  }, [snackbar]);

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
    const readPermission = async (): Promise<void> => {
      if (isTauriRuntime()) {
        setGeoPermissionState("native");
        return;
      }

      if (typeof navigator === "undefined" || !("permissions" in navigator)) {
        setGeoPermissionState("unsupported");
        return;
      }

      try {
        const permissionsApi = navigator.permissions as Permissions;
        const state = await permissionsApi.query({ name: "geolocation" as PermissionName });
        setGeoPermissionState(state.state);
        state.onchange = () => {
          setGeoPermissionState(state.state);
        };
      } catch {
        setGeoPermissionState("unknown");
      }
    };

    void readPermission();
  }, []);

  useEffect(() => {
    const onWheel = (event: WheelEvent): void => {
      if (event.ctrlKey || event.metaKey) {
        event.preventDefault();
      }
    };

    const onKeyDown = (event: KeyboardEvent): void => {
      if (!(event.ctrlKey || event.metaKey)) {
        return;
      }

      if (event.key === "+" || event.key === "=" || event.key === "-" || event.key === "0") {
        event.preventDefault();
      }
    };

    window.addEventListener("wheel", onWheel, { passive: false });
    window.addEventListener("keydown", onKeyDown);

    return () => {
      window.removeEventListener("wheel", onWheel);
      window.removeEventListener("keydown", onKeyDown);
    };
  }, []);

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

  useEffect(() => {
    if (!latestPoint) {
      setMapFocusPoint(null);
      return;
    }

    setMapFocusPoint(latestPoint);
  }, [latestPoint, selectedMapDeviceId]);

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

  const linuxActionDevices = useMemo(
    () => devices.filter((entry) => entry.type === "Linux" && entry.isOnline && entry.id !== myDeviceId),
    [devices, myDeviceId],
  );

  const selectedActionDevice = useMemo(
    () => linuxActionDevices.find((entry) => entry.id === selectedActionDeviceId) ?? null,
    [linuxActionDevices, selectedActionDeviceId],
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
        .filter((entry) => devices.some((device) => device.id === entry.deviceId && device.id !== myDeviceId))
      .sort((left, right) => right.lastMessage.sentAt - left.lastMessage.sentAt);
  }, [devices, messages, myDeviceId]);

  useEffect(() => {
    if (
      selectedFileTargetDeviceId
      && devices.some((entry) => entry.id === selectedFileTargetDeviceId && entry.id !== myDeviceId && (entry.isOnline || entry.isBluetoothOnline === true))
    ) {
      return;
    }

    const fallback = devices.find((entry) => entry.id !== myDeviceId && (entry.isOnline || entry.isBluetoothOnline === true))?.id ?? "";
    setSelectedFileTargetDeviceId(fallback);
  }, [devices, myDeviceId, selectedFileTargetDeviceId]);

  useEffect(() => {
    cacheStore.setMessages(messages);
  }, [messages]);

  useEffect(() => {
    cacheStore.setNotificationHistory(notificationHistory);
  }, [notificationHistory]);

  useEffect(() => {
    cacheStore.setNotificationSchedules(notificationSchedules);
  }, [notificationSchedules]);

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
    setNotes(next.map((note) => ({
      ...note,
      contentType: normalizeNoteType(note.contentType),
    })));
  };

  const refreshTransfers = async (): Promise<void> => {
    const next = await api.getTransfers(myDeviceId);
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

  const applyLiveLocationUpdate = (payload: LocationUpdateEvent): void => {
    const currentPoint: LocationPoint = {
      id: `current-${payload.deviceId}`,
      title: "Live",
      recordedAt: payload.recordedAt,
      latitude: payload.latitude,
      longitude: payload.longitude,
    };

    setHistory((prev) => {
      const withoutCurrent = prev.filter((entry) => entry.id !== `current-${payload.deviceId}`);
      return [currentPoint, ...withoutCurrent].slice(0, 500);
    });

    if (selectedMapDeviceId === payload.deviceId) {
      setMapFocusPoint(currentPoint);
    }
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

  const getCurrentPosition = async (): Promise<{ latitude: number; longitude: number; accuracyMeters: number | null }> => {
    if (isTauriRuntime()) {
      const nativeLocation = await getNativeLocation();
      if (!nativeLocation) {
        setLastGeoError(
          isElevatedRuntime
            ? "Native location is blocked in full-app sudo mode (desktop session permission denied)"
            : "Native Linux location unavailable or permission denied",
        );
        throw new Error("Native location is unavailable. Ensure desktop location services are enabled.");
      }

      setLastGeoError("");
      setLastLocationSampleAt(new Date().toISOString());
      setLastLocationSource("native");

      return {
        latitude: nativeLocation.latitude,
        longitude: nativeLocation.longitude,
        accuracyMeters: nativeLocation.accuracyMeters,
      };
    }

    if (typeof navigator === "undefined" || !navigator.geolocation) {
      setLastGeoError("Geolocation API is unavailable in this runtime");
      throw new Error("Location services are unavailable on this device");
    }

    const position = await new Promise<GeolocationPosition>((resolve, reject) => {
      navigator.geolocation.getCurrentPosition(
        resolve,
        (error) => {
          setLastGeoError(`${error.code}: ${error.message}`);
          reject(error);
        },
        { enableHighAccuracy: true, timeout: 10_000 },
      );
    });

    setLastGeoError("");
    setLastLocationSampleAt(new Date().toISOString());
    setLastLocationSource("geolocation");

    return {
      latitude: position.coords.latitude,
      longitude: position.coords.longitude,
      accuracyMeters: Number.isFinite(position.coords.accuracy) ? position.coords.accuracy : null,
    };
  };

  const requestLocationPermission = async (): Promise<boolean> => {
    try {
      if (isTauriRuntime()) {
        setGeoPermissionState("native");
        await getCurrentPosition();
        return true;
      }

      if (typeof navigator !== "undefined" && "permissions" in navigator) {
        const permissionsApi = navigator.permissions as Permissions;
        const state = await permissionsApi.query({ name: "geolocation" as PermissionName });
        setGeoPermissionState(state.state);
      }

      await getCurrentPosition();
      return true;
    } catch {
      setError("Unable to acquire location because location permission was not granted");
      return false;
    }
  };

  const toggleLocationSharing = async (enabled: boolean): Promise<void> => {
    if (!enabled) {
      setLocationEnabled(false);
      settingsStore.setLocationEnabled(false);
      return;
    }

    if (isElevatedRuntime) {
      setLocationEnabled(false);
      settingsStore.setLocationEnabled(false);
      setError("Background location sharing is disabled in full-app sudo mode. Run BinaryStars normally.");
      return;
    }

    const confirmed = window.confirm(
      "Allow BinaryStars to share this device location in the background while this toggle is on?",
    );
    if (!confirmed) {
      setLocationEnabled(false);
      settingsStore.setLocationEnabled(false);
      return;
    }

    const granted = await requestLocationPermission();
    if (!granted) {
      setLocationEnabled(false);
      settingsStore.setLocationEnabled(false);
      setError("Location sharing could not start because location permission is required");
      return;
    }

    setError("");
    setLocationEnabled(true);
    settingsStore.setLocationEnabled(true);
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

  const syncNotificationsFromServer = async (surfaceError = false): Promise<void> => {
    if (!isAuthed || !online) {
      return;
    }

    try {
      const payload = await api.pullNotifications(myDeviceId);
      setNotificationSchedules(payload.schedules);

      if (payload.notifications.length > 0) {
        setNotificationHistory((prev) => {
          const byId = new Map(prev.map((entry) => [entry.id, entry]));
          payload.notifications.forEach((entry) => {
            if (!byId.has(entry.id)) {
              invoke("show_notification", { title: entry.title, body: entry.body }).catch(console.error);
            }
            byId.set(entry.id, {
              ...entry,
              receivedAt: new Date().toISOString(),
            });
          });

          return Array.from(byId.values())
            .sort((left, right) => Date.parse(right.createdAt) - Date.parse(left.createdAt))
            .slice(0, 1_000);
        });
      }

      await api.acknowledgeNotificationSync(myDeviceId);
      setDevices((prev) => prev.map((device) => {
        if (device.id !== myDeviceId) {
          return device;
        }

        return {
          ...device,
          hasPendingNotificationSync: false,
        };
      }));
    } catch (nextError) {
      if (surfaceError) {
        setError(nextError instanceof Error ? nextError.message : "Failed to sync notifications");
      }
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
    if (!isAuthed || !online || activeTab !== "Devices") {
      return;
    }

    const refresh = (): void => {
      void refreshDevices().catch(() => {
        setError("No connection available");
      });
    };

    refresh();
    const timer = window.setInterval(refresh, 10_000);
    return () => window.clearInterval(timer);
  }, [activeTab, isAuthed, online, bluetoothConnectedNames]);

  useEffect(() => {
    if (!isAuthed) {
      return;
    }

    const refreshBluetoothPresence = async (): Promise<void> => {
      const knownDevices = await getBluetoothDevices();
      const names = knownDevices
        .filter((entry) => entry.connected)
        .map((entry) => entry.name);
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
    if (!isAuthed || !online) {
      return;
    }

    void flushPendingLocationUploads(myDeviceId);
  }, [isAuthed, online, myDeviceId]);

  useEffect(() => {
    void refreshElevationStatus().catch((nextError) => {
      setError(nextError instanceof Error ? nextError.message : "Failed to check elevation status");
    });
  }, [isAuthed]);

  useEffect(() => {
    if (!isElevatedRuntime) {
      return;
    }

    if (locationEnabled) {
      setLocationEnabled(false);
      settingsStore.setLocationEnabled(false);
    }

    setLastLocationSource(null);
  }, [isElevatedRuntime, locationEnabled]);

  useEffect(() => {
    if (!isAuthed || !online || activeTab !== "Map" || !selectedMapDeviceId) {
      return;
    }

    const timer = window.setInterval(() => {
      void refreshMapHistory(selectedMapDeviceId);
    }, 10_000);

    return () => window.clearInterval(timer);
  }, [activeTab, isAuthed, online, selectedMapDeviceId]);

  useEffect(() => {
    if (!isAuthed || !online || activeTab !== "Notifications" || !notificationTargetDeviceId) {
      return;
    }

    const refreshSchedules = async (): Promise<void> => {
      try {
        const items = await api.getNotificationSchedules(notificationTargetDeviceId);
        setNotificationSchedules(items);
      } catch {
        // keep cached schedules
      }
    };

    void refreshSchedules();
  }, [activeTab, isAuthed, online, notificationTargetDeviceId]);

  useEffect(() => {
    if (!isAuthed) {
      return;
    }
    let cancelled = false;
    let reconnectTimer: number | null = null;
    let activeSocket: WebSocket | null = null;

    const connectSocket = (): void => {
      if (activeSocket && activeSocket.readyState !== WebSocket.CLOSED) {
        return;
      }

      const token = tokenStore.getStoredToken();
      if (!token) {
        logEvent("warn", "ws", "connect-skipped-no-token", { deviceId: myDeviceId });
        return;
      }

      const socketUrl = toWsUrl(myDeviceId, token);
      logEvent("info", "ws", "connect-attempt", { deviceId: myDeviceId });
      const socket = new WebSocket(socketUrl);
      activeSocket = socket;
      wsRef.current = socket;

      socket.onopen = () => {
        logEvent("info", "ws", "connected", { deviceId: myDeviceId });
      };

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

        if (messageType === "location_update") {
          const payload = envelope.payload as LocationUpdateEvent;
          if (payload.deviceId === selectedMapDeviceIdRef.current) {
            applyLiveLocationUpdate(payload);
          }
          return;
        }

        if (messageType === "action_command") {
          const payload = envelope.payload as DeviceActionCommand;
          logEvent("debug", "ws.actions", "action-command-received", {
            actionType: payload.actionType,
            senderDeviceId: payload.senderDeviceId,
            correlationId: payload.correlationId,
          });
          void handleRealtimeActionCommand(payload);
          return;
        }

        if (messageType === "action_result") {
          const payload = envelope.payload as DeviceActionResultMessage;
          logEvent("debug", "ws.actions", "action-result-received", {
            actionType: payload.actionType,
            status: payload.status,
            senderDeviceId: payload.senderDeviceId,
            correlationId: payload.correlationId,
          });
          handleRealtimeActionResult(payload);
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

      socket.onerror = () => {
        logEvent("warn", "ws", "error", { deviceId: myDeviceId });
      };

      socket.onclose = (event) => {
        logEvent("warn", "ws", "closed", {
          deviceId: myDeviceId,
          code: event.code,
          reason: event.reason,
        });

        if (activeSocket === socket) {
          activeSocket = null;
        }

        if (wsRef.current === socket) {
          wsRef.current = null;
        }

        if (cancelled) {
          return;
        }

        if (reconnectTimer != null) {
          window.clearTimeout(reconnectTimer);
        }

        reconnectTimer = window.setTimeout(() => {
          if (cancelled || !isAuthed) {
            return;
          }

          connectSocket();
        }, 2000);
      };
    };

    connectSocket();

    return () => {
      cancelled = true;
      if (reconnectTimer != null) {
        window.clearTimeout(reconnectTimer);
      }

      const socketToClose = activeSocket;
      activeSocket = null;

      if (socketToClose && socketToClose.readyState !== WebSocket.CLOSED) {
        socketToClose.close();
      }

      if (wsRef.current === socketToClose) {
        wsRef.current = null;
      }
    };
  }, [isAuthed, myDeviceId]);

  usePresenceHeartbeat(isAuthed, myDeviceId, (device) => {
    if (!device.hasPendingNotificationSync) {
      return;
    }

    void syncNotificationsFromServer();
  });

  useEffect(() => {
    if (!isAuthed) {
      return;
    }
    settingsStore.setLocationEnabled(locationEnabled);
    settingsStore.setLocationMinutes(locationMinutes);

    if (!locationEnabled) {
      return;
    }

    let cancelled = false;

    const ensurePermission = async (): Promise<boolean> => {
      const granted = await requestLocationPermission();
      if (!granted && !cancelled) {
        setLocationEnabled(false);
        settingsStore.setLocationEnabled(false);
        setError("Location sharing was disabled because location permission is required");
      }
      return granted;
    };

    const runLive = async (): Promise<void> => {
      try {
        const position = await getCurrentPosition();
        const payload = {
          deviceId: myDeviceId,
          latitude: position.latitude,
          longitude: position.longitude,
          accuracyMeters: position.accuracyMeters,
          recordedAt: new Date().toISOString(),
        };

        if (!online) {
          return;
        }

        await api.sendLiveLocation(payload);
      } catch {
        // no-op
      }
    };

    const runPersisted = async (): Promise<void> => {
      try {
        const position = await getCurrentPosition();
        const recordedAt = new Date().toISOString();
        const payload = {
          deviceId: myDeviceId,
          latitude: position.latitude,
          longitude: position.longitude,
          accuracyMeters: position.accuracyMeters,
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

        await flushPendingLocationUploads(myDeviceId);
        await api.sendLocation(payload);

        if (selectedMapDeviceId === myDeviceId) {
          await refreshMapHistory(myDeviceId);
        }
      } catch {
        // no-op
      }
    };

    void (async () => {
      const granted = await ensurePermission();
      if (!granted || cancelled) {
        return;
      }
      setError("");
      await runLive();
      await runPersisted();
    })();

    const liveTimer = window.setInterval(() => {
      void runLive();
    }, 15_000);
    const persistedTimer = window.setInterval(() => {
      void runPersisted();
    }, locationMinutes * 60_000);
    return () => {
      cancelled = true;
      window.clearInterval(liveTimer);
      window.clearInterval(persistedTimer);
    };
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

  useEffect(() => {
    if (!selectedDeviceDetail) {
      clipboardAutoFetchKeyRef.current = "";
      setClipboardHistory([]);
      setClipboardHistoryMessage("");
      setClipboardHistoryLoading(false);
      setPendingClipboardRequestId(null);
      setClipboardTargetDeviceId(null);
      return;
    }

    const key = `${selectedDeviceDetail.id}:${selectedDeviceDetail.type}:${selectedDeviceDetail.isOnline}`;
    if (clipboardAutoFetchKeyRef.current === key) {
      return;
    }

    clipboardAutoFetchKeyRef.current = key;

    if (selectedDeviceDetail.type === "Android") {
      setClipboardHistory([]);
      setClipboardHistoryLoading(false);
      setPendingClipboardRequestId(null);
      setClipboardTargetDeviceId(null);
      setClipboardHistoryMessage("Clipboard history for Android targets is not available due OS-level clipboard access restrictions.");
      return;
    }

    if (!selectedDeviceDetail.isOnline) {
      setClipboardHistory([]);
      setClipboardHistoryLoading(false);
      setPendingClipboardRequestId(null);
      setClipboardTargetDeviceId(null);
      setClipboardHistoryMessage("Target device must be online to fetch clipboard history.");
      return;
    }

    const requestClipboardHistory = async (): Promise<void> => {
      if (!isAuthed || !online) {
        return;
      }

      const requestId = crypto.randomUUID();
      setClipboardHistory([]);
      setClipboardHistoryMessage("");
      setClipboardHistoryLoading(true);
      setPendingClipboardRequestId(requestId);
      setClipboardTargetDeviceId(selectedDeviceDetail.id);

      try {
        await sendActionCommand("get_clipboard_history", null, requestId, selectedDeviceDetail.id);
      } catch {
        setClipboardHistoryLoading(false);
        setPendingClipboardRequestId(null);
        setClipboardTargetDeviceId(null);
        setClipboardHistoryMessage("Failed to request clipboard history.");
      }
    };

    void requestClipboardHistory();
  }, [isAuthed, myDeviceId, online, selectedDeviceDetail]);

  useEffect(() => {
    if (notificationTargetDeviceId && devices.some((entry) => entry.id === notificationTargetDeviceId)) {
      return;
    }

    if (devices.length > 0) {
      const fallback = devices.find((entry) => entry.id === myDeviceId)?.id ?? devices[0].id;
      setNotificationTargetDeviceId(fallback);
    }
  }, [devices, myDeviceId, notificationTargetDeviceId]);

  useEffect(() => {
    if (selectedActionDeviceId && linuxActionDevices.some((entry) => entry.id === selectedActionDeviceId)) {
      return;
    }

    if (linuxActionDevices.length > 0) {
      const fallback = linuxActionDevices.find((entry) => entry.id !== myDeviceId)?.id ?? linuxActionDevices[0].id;
      setSelectedActionDeviceId(fallback);
    } else {
      setSelectedActionDeviceId("");
    }
  }, [linuxActionDevices, myDeviceId, selectedActionDeviceId]);

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
      type: "Linux",
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

  const unlinkDevice = async (deviceId: string): Promise<void> => {
    if (!requireOnline()) {
      return;
    }

    await api.unlinkDevice(deviceId);
    await refreshDevices();
  };

  const unlinkCurrentDevice = async (): Promise<void> => {
    await unlinkDevice(myDeviceId);
  };

  const sendFile = async (file: File, targetDeviceId: string): Promise<void> => {
    const targetDevice = devices.find((entry) => entry.id === targetDeviceId);
    if (!targetDevice || targetDevice.id === myDeviceId) {
      setError("No target device available");
      return;
    }

    if (!online) {
      if (!targetDevice.isBluetoothOnline) {
        setError("Internet is unavailable and the target device is not connected over Bluetooth");
        return;
      }

      const bluetoothPeers = await getBluetoothDevices();
      const matchedPeer = bluetoothPeers.find((entry) => {
        const left = entry.name.trim().toLowerCase();
        const right = targetDevice.name.trim().toLowerCase();
        return entry.connected && left.length > 0 && left === right;
      });

      if (!matchedPeer?.address) {
        setError("Unable to resolve Bluetooth target address. Reconnect the device and retry");
        return;
      }

      const contentBase64 = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => {
          const result = typeof reader.result === "string" ? reader.result : "";
          const marker = "base64,";
          const markerIndex = result.indexOf(marker);
          if (markerIndex < 0) {
            reject(new Error("Failed to encode file for Bluetooth transfer"));
            return;
          }
          resolve(result.slice(markerIndex + marker.length));
        };
        reader.onerror = () => reject(new Error("Failed to read file"));
        reader.readAsDataURL(file);
      });

      await sendFileViaBluetooth(matchedPeer.address, file.name, contentBase64);
      return;
    }

    console.log(`[FileTransfer] Initiating send of file: ${file.name} (${file.size} bytes) to device ${targetDeviceId}`);

    const created = await api.createTransfer({
      fileName: file.name,
      contentType: file.type || "application/octet-stream",
      sizeBytes: file.size,
      senderDeviceId: myDeviceId,
      targetDeviceId,
      encryptionEnvelope: JSON.stringify({ alg: "none" }),
    });
    
    console.log(`[FileTransfer] Transfer created with ID: ${created.id}. Starting upload...`);
    await api.uploadTransfer(created.id, file);
    console.log(`[FileTransfer] Upload completed for transfer ID: ${created.id}`);
    await refreshTransfers();
  };

  const onPickFile = async (event: ChangeEvent<HTMLInputElement>): Promise<void> => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }

    if (!selectedFileTargetDeviceId) {
      setError("No target device available");
      return;
    }

    try {
      setBusy(true);
      await sendFile(file, selectedFileTargetDeviceId);
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

    console.log(`[FileTransfer] Initiating download for transfer ID: ${transfer.id}, File: ${transfer.fileName}`);
    try {
      const blob = await api.downloadTransfer(transfer.id, myDeviceId);
      console.log(`[FileTransfer] Download successful, blob size: ${blob.size} bytes`);
      
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = transfer.fileName;
      anchor.click();
      URL.revokeObjectURL(url);
      await refreshTransfers();
    } catch (error) {
      console.error(`[FileTransfer] Error downloading transfer ID: ${transfer.id}`, error);
      setError("Failed to download file");
    }
  };

  const rejectTransfer = async (transfer: FileTransfer): Promise<void> => {
    if (!requireOnline()) {
      return;
    }

    console.log(`[FileTransfer] Rejecting transfer ID: ${transfer.id}`);
    await api.rejectTransfer(transfer.id, myDeviceId);
    await refreshTransfers();
  };

  const clearTransfersByScope = async (scope: "sent" | "received"): Promise<void> => {
    if (!requireOnline()) {
      return;
    }

    await api.clearTransfers(myDeviceId, scope);
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
    setNoteType(normalizeNoteType(note.contentType));
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
    handleTabSelect("Messaging");
  };

  const refreshConversationHistory = async (targetDeviceId: string): Promise<void> => {
    if (!isAuthed || !online || !targetDeviceId) {
      return;
    }

    const history = await api.getMessageHistory(myDeviceId, targetDeviceId, 200);
    const mapped: ChatMessage[] = history.map((entry) => ({
      id: entry.id,
      deviceId: entry.senderDeviceId === myDeviceId ? entry.targetDeviceId : entry.senderDeviceId,
      senderDeviceId: entry.senderDeviceId,
      body: entry.body,
      sentAt: Date.parse(entry.sentAt),
      isOutgoing: entry.senderDeviceId === myDeviceId,
    }));

    setMessages((prev) => {
      const withoutCurrentConversation = prev.filter((entry) => entry.deviceId !== targetDeviceId);
      const merged = [...withoutCurrentConversation];
      mapped.forEach((entry) => {
        const found = merged.findIndex((candidate) => candidate.id === entry.id);
        if (found >= 0) {
          merged[found] = entry;
        } else {
          merged.push(entry);
        }
      });

      return merged.sort((left, right) => left.sentAt - right.sentAt);
    });
  };

  const flushPendingChatMessages = async (): Promise<void> => {
    if (!isAuthed || !online) {
      return;
    }

    const pending = cacheStore.getPendingChatMessages();
    if (pending.length === 0) {
      return;
    }

    for (const queued of pending) {
      try {
        const sent = await api.sendMessage({
          senderDeviceId: queued.senderDeviceId,
          targetDeviceId: queued.targetDeviceId,
          body: queued.body,
          sentAt: queued.sentAt,
        });

        const sentAt = Date.parse(sent.sentAt);
        setMessages((prev) => {
          const withoutQueued = prev.filter((entry) => entry.id !== queued.localMessageId);
          return upsertMessage(withoutQueued, {
            id: sent.id,
            deviceId: queued.targetDeviceId,
            senderDeviceId: sent.senderDeviceId,
            body: sent.body,
            sentAt,
            isOutgoing: true,
          });
        });

        cacheStore.removePendingChatMessage(queued.localMessageId);
      } catch {
        // Keep queued message for next retry cycle.
      }
    }
  };

  useEffect(() => {
    if (!isAuthed || !online) {
      return;
    }

    void flushPendingChatMessages();
  }, [isAuthed, online]);

  useEffect(() => {
    if (!selectedChatDeviceId) {
      return;
    }

    void refreshConversationHistory(selectedChatDeviceId);

    const intervalId = setInterval(() => {
      void refreshConversationHistory(selectedChatDeviceId);
    }, 3000);

    return () => clearInterval(intervalId);
  }, [isAuthed, myDeviceId, online, selectedChatDeviceId]);

  const openDeviceDetail = (deviceId: string): void => {
    setSelectedDeviceDetailId(deviceId);
  };

  const sendChatMessage = async (): Promise<void> => {
    const body = newMessage.trim();
    if (!selectedChatDeviceId || !body) {
      return;
    }

    const targetDevice = devices.find((entry) => entry.id === selectedChatDeviceId);
    if (!targetDevice || targetDevice.id === myDeviceId) {
      setError("Target device must be online");
      return;
    }

    if (!requireOnline()) {
      if (!targetDevice.isBluetoothOnline) {
        setError("Target device must be online or connected over Bluetooth");
        return;
      }

      const localMessageId = crypto.randomUUID();
      const sentAtIso = new Date().toISOString();

      cacheStore.addPendingChatMessage({
        localMessageId,
        senderDeviceId: myDeviceId,
        targetDeviceId: selectedChatDeviceId,
        body,
        sentAt: sentAtIso,
      });

      setMessages((prev) => upsertMessage(prev, {
        id: localMessageId,
        deviceId: selectedChatDeviceId,
        senderDeviceId: myDeviceId,
        body,
        sentAt: Date.parse(sentAtIso),
        isOutgoing: true,
      }));

      setNewMessage("");
      setInfo("Offline mode: message queued and will send automatically when internet returns");
      return;
    }

    if (!targetDevice.isOnline) {
      setError("Target device must be online");
      return;
    }

    const request = {
      senderDeviceId: myDeviceId,
      targetDeviceId: selectedChatDeviceId,
      body,
      sentAt: new Date().toISOString(),
    };

    try {
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
      setNewMessage("");
      setSuccess("Message sent");
    } catch {
      setError("Failed to send message.");
    }
  };

  const resetNotificationEditor = (): void => {
    setEditingNotificationScheduleId(null);
    setNotificationTitle("");
    setNotificationBody("");
    setNotificationScheduledFor("");
    setNotificationRepeatMinutes("");
    setNotificationScheduleEnabled(true);
  };

  const sendNotificationNow = async (): Promise<void> => {
    if (!requireOnline()) {
      return;
    }

    if (!notificationTargetDeviceId || !notificationTitle.trim() || !notificationBody.trim()) {
      setError("Target device, title, and body are required");
      return;
    }

    await api.sendNotification({
      senderDeviceId: myDeviceId,
      targetDeviceId: notificationTargetDeviceId,
      title: notificationTitle.trim(),
      body: notificationBody.trim(),
    });

    if (notificationTargetDeviceId === myDeviceId) {
      await syncNotificationsFromServer();
    }

    setError("");
  };

  const saveNotificationSchedule = async (): Promise<void> => {
    if (!requireOnline()) {
      return;
    }

    if (!notificationTargetDeviceId || !notificationTitle.trim() || !notificationBody.trim()) {
      setError("Target device, title, and body are required");
      return;
    }

    const hasScheduledFor = notificationScheduledFor.trim().length > 0;
    const hasRepeat = notificationRepeatMinutes.trim().length > 0;

    if ((hasScheduledFor && hasRepeat) || (!hasScheduledFor && !hasRepeat)) {
      setError("Provide either one-time schedule or repeat minutes");
      return;
    }

    const payload = {
      sourceDeviceId: myDeviceId,
      targetDeviceId: notificationTargetDeviceId,
      title: notificationTitle.trim(),
      body: notificationBody.trim(),
      isEnabled: notificationScheduleEnabled,
      scheduledForUtc: hasScheduledFor ? new Date(notificationScheduledFor).toISOString() : null,
      repeatMinutes: hasRepeat ? Number.parseInt(notificationRepeatMinutes, 10) : null,
    };

    if (editingNotificationScheduleId) {
      await api.updateNotificationSchedule(editingNotificationScheduleId, payload);
    } else {
      await api.createNotificationSchedule(payload);
    }

    if (notificationTargetDeviceId === myDeviceId) {
      await syncNotificationsFromServer();
    } else {
      const refreshed = await api.getNotificationSchedules(notificationTargetDeviceId);
      setNotificationSchedules(refreshed);
    }

    setError("");
    resetNotificationEditor();
  };

  const editNotificationSchedule = (schedule: NotificationSchedule): void => {
    setEditingNotificationScheduleId(schedule.id);
    setNotificationTargetDeviceId(schedule.targetDeviceId);
    setNotificationTitle(schedule.title);
    setNotificationBody(schedule.body);
    setNotificationScheduleEnabled(schedule.isEnabled);
    setNotificationRepeatMinutes(schedule.repeatMinutes != null ? String(schedule.repeatMinutes) : "");
    setNotificationScheduledFor(
      schedule.scheduledForUtc ? new Date(schedule.scheduledForUtc).toISOString().slice(0, 16) : "",
    );
  };

  const deleteNotificationSchedule = async (scheduleId: string): Promise<void> => {
    if (!requireOnline()) {
      return;
    }

    await api.deleteNotificationSchedule(scheduleId);

    if (notificationTargetDeviceId === myDeviceId) {
      await syncNotificationsFromServer();
    } else {
      const refreshed = await api.getNotificationSchedules(notificationTargetDeviceId);
      setNotificationSchedules(refreshed);
    }

    if (editingNotificationScheduleId === scheduleId) {
      resetNotificationEditor();
    }
  };

  const deleteNotificationHistoryItem = (itemId: string): void => {
    setNotificationHistory((prev) => prev.filter((entry) => entry.id !== itemId));
  };

  const clearNotificationHistory = (): void => {
    setNotificationHistory([]);
  };

  const clearCurrentChat = (): void => {
    if (!selectedChatDeviceId) {
      return;
    }

    if (!online) {
      setError("No connection available");
      return;
    }

    void (async () => {
      try {
        await api.clearConversation(myDeviceId, selectedChatDeviceId);
        setMessages((prev) => prev.filter((entry) => entry.deviceId !== selectedChatDeviceId));
      } catch {
        setError("Failed to clear conversation");
      }
    })();
  };

  const sendSocketEnvelope = (type: string, payload: unknown): boolean => {
    const socket = wsRef.current;
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      logEvent("warn", "ws", "send-skipped-socket-not-open", { type });
      return false;
    }

    logEvent("debug", "ws", "send-envelope", { type });
    socket.send(JSON.stringify({ type, payload }));
    return true;
  };

  const withTimeout = async <T,>(promise: Promise<T>, timeoutMs: number, errorMessage: string): Promise<T> => {
    let timer: number | null = null;

    try {
      return await Promise.race<T>([
        promise,
        new Promise<T>((_, reject) => {
          timer = window.setTimeout(() => {
            reject(new Error(errorMessage));
          }, timeoutMs);
        }),
      ]);
    } finally {
      if (timer != null) {
        window.clearTimeout(timer);
      }
    }
  };

  const deliverActionResult = async (result: {
    senderDeviceId: string;
    targetDeviceId: string;
    actionType: string;
    status: string;
    payloadJson: string | null;
    error: string | null;
    correlationId: string;
  }): Promise<void> => {
    const deliveredBySocket = sendSocketEnvelope("action_result", result);
    if (deliveredBySocket) {
      logEvent("debug", "actions", "action-result-delivered-via-ws", {
        actionType: result.actionType,
        correlationId: result.correlationId,
        status: result.status,
      });
      return;
    }

    logEvent("warn", "actions", "action-result-ws-delivery-failed", {
      actionType: result.actionType,
      correlationId: result.correlationId,
      status: result.status,
    });

    throw new Error("Realtime socket is not connected; action result not delivered.");
  };

  const handleRealtimeActionResult = (result: DeviceActionResultMessage): void => {
    if (result.targetDeviceId !== myDeviceId) {
      return;
    }

    if (result.actionType === "get_clipboard_history") {
      if (pendingClipboardRequestId && result.correlationId !== pendingClipboardRequestId) {
        return;
      }

      if (clipboardTargetDeviceId && result.senderDeviceId !== clipboardTargetDeviceId) {
        return;
      }

      if (result.status.toLowerCase() !== "success") {
        setClipboardHistory([]);
        setClipboardHistoryMessage(result.error ?? "Failed to fetch clipboard history from target device.");
        setClipboardHistoryLoading(false);
        setPendingClipboardRequestId(null);
        setClipboardTargetDeviceId(null);
        return;
      }

      try {
        const parsed = JSON.parse(result.payloadJson ?? "[]") as unknown[];
        const values = parsed
          .map((entry) => {
            if (typeof entry === "string") {
              return entry;
            }

            if (entry && typeof entry === "object" && "content" in entry) {
              const content = (entry as { content?: unknown }).content;
              return typeof content === "string" ? content : "";
            }

            return "";
          })
          .map((entry) => entry.trim())
          .filter((entry) => entry.length > 0);

        setClipboardHistory(values);
        setClipboardHistoryMessage(values.length === 0 ? "No clipboard history available." : "");
      } catch {
        setClipboardHistory([]);
        setClipboardHistoryMessage("Failed to parse clipboard history payload.");
      }

      setClipboardHistoryLoading(false);
      setPendingClipboardRequestId(null);
      setClipboardTargetDeviceId(null);
      return;
    }

    if (result.actionType === "list_installed_apps" && result.status.toLowerCase() === "partial") {
      if (pendingActionRequestId && result.correlationId !== pendingActionRequestId) {
        return;
      }

      try {
        const parsed = JSON.parse(result.payloadJson ?? "[]") as LaunchableAppItem[];
        setLaunchableApps((prev) => {
          const byExec = new Map(prev.map((entry) => [entry.exec, entry] as const));
          for (const app of parsed) {
            byExec.set(app.exec, app);
          }
          return Array.from(byExec.values()).sort((left, right) => left.name.localeCompare(right.name));
        });
      } catch {
        setError("Failed to parse partial installed apps payload");
      }

      return;
    }

    if (result.status.toLowerCase() !== "success") {
      setError(result.error ?? `Action ${result.actionType} failed`);
      if (pendingActionRequestId && result.correlationId === pendingActionRequestId) {
        clearPendingAction();
      }
      return;
    }

    if (pendingActionRequestId && result.correlationId !== pendingActionRequestId) {
      return;
    }

    if (result.actionType === "list_installed_apps") {
      try {
        const parsed = JSON.parse(result.payloadJson ?? "[]") as LaunchableAppItem[];
        setLaunchableApps(parsed);
      } catch {
        setError("Failed to parse installed apps payload");
      }
      clearPendingAction();
      return;
    }

    if (result.actionType === "list_running_apps") {
      try {
        const parsed = JSON.parse(result.payloadJson ?? "[]") as RunningAppItem[];
        setRunningApps(parsed);
      } catch {
        setError("Failed to parse running apps payload");
      }
      clearPendingAction();
      return;
    }

    if (result.actionType === "launch_app" || result.actionType === "close_app") {
      clearPendingAction();
    }
  };

  const executeActionLocally = async (actionType: string, payloadJson?: string | null): Promise<string | null> => {
    if (actionType === "block_screen") {
      await performLocalAction("block_screen");
      return null;
    }

    if (actionType === "shutdown") {
      await performLocalAction("shutdown");
      return null;
    }

    if (actionType === "reboot" || actionType === "reset") {
      await performLocalAction("reboot");
      return null;
    }

    if (actionType === "list_installed_apps") {
      return performLocalAction("list_installed_apps");
    }

    if (actionType === "list_running_apps") {
      return performLocalAction("list_running_apps");
    }

    if (actionType === "launch_app") {
      await performLocalAction("launch_app", payloadJson);
      return null;
    }

    if (actionType === "close_app") {
      await performLocalAction("close_app", payloadJson);
      return null;
    }

    if (actionType === "get_clipboard_history") {
      return performLocalAction("get_clipboard_history");
    }

    return null;
  };

  const getLocalActionExecutionTimeoutMs = (): number => {
    return 30_000;
  };

  const handleRealtimeActionCommand = async (command: DeviceActionCommand): Promise<void> => {
    if (command.targetDeviceId !== myDeviceId) {
      return;
    }

    try {
      logEvent("info", "actions", "execute-action-command", {
        actionType: command.actionType,
        correlationId: command.correlationId,
      });
      const payload = await withTimeout(
        executeActionLocally(command.actionType, command.payloadJson ?? null),
        getLocalActionExecutionTimeoutMs(),
        "Local action execution timed out",
      );

      if (command.actionType === "list_installed_apps" && payload) {
        try {
          const apps = JSON.parse(payload) as LaunchableAppItem[];
          const chunkSize = 10;
          for (let index = 0; index < apps.length; index += chunkSize) {
            const chunk = apps.slice(index, index + chunkSize);
            await deliverActionResult({
              senderDeviceId: myDeviceId,
              targetDeviceId: command.senderDeviceId,
              actionType: command.actionType,
              status: "partial",
              payloadJson: JSON.stringify(chunk),
              error: null,
              correlationId: command.correlationId ?? command.id,
            });
          }
        } catch {
          // ignore chunking failures and continue with final full payload result
        }
      }

      await deliverActionResult({
        senderDeviceId: myDeviceId,
        targetDeviceId: command.senderDeviceId,
        actionType: command.actionType,
        status: "success",
        payloadJson: payload,
        error: null,
        correlationId: command.correlationId ?? command.id,
      });
    } catch (nextError) {
      const message = nextError instanceof Error ? nextError.message : "Failed to execute remote action";
      logEvent("error", "actions", "execute-action-command-failed", {
        actionType: command.actionType,
        correlationId: command.correlationId,
        error: message,
      });
      try {
        await deliverActionResult({
          senderDeviceId: myDeviceId,
          targetDeviceId: command.senderDeviceId,
          actionType: command.actionType,
          status: "failed",
          payloadJson: null,
          error: message,
          correlationId: command.correlationId ?? command.id,
        });
      } catch (resultError) {
        const resultErrorMessage = resultError instanceof Error ? resultError.message : "unknown";
        logEvent("error", "actions", "action-result-delivery-failed", {
          actionType: command.actionType,
          correlationId: command.correlationId,
          error: resultErrorMessage,
        });
      }
      return;
    }
  };

  const sendActionCommand = async (
    actionType:
      | "block_screen"
      | "shutdown"
      | "reboot"
      | "list_installed_apps"
      | "list_running_apps"
      | "launch_app"
      | "close_app"
      | "get_clipboard_history",
    payloadJson?: string | null,
    correlationId?: string | null,
    targetDeviceId?: string,
  ): Promise<boolean> => {
    const requestedTargetId = targetDeviceId ?? selectedActionDevice?.id;
    if (!requestedTargetId) {
      setError("Select a Linux device first");
      return false;
    }

    const targetDevice = devices.find((entry) => entry.id === requestedTargetId) ?? null;
    if (!targetDevice || targetDevice.type !== "Linux") {
      setError("Target must be a Linux device");
      return false;
    }

    if (!requireOnline()) {
      setError("No connection available");
      return false;
    }

    if (!targetDevice.isOnline) {
      setError("Target device must be online");
      return false;
    }

    const sent = sendSocketEnvelope("action_command", {
      senderDeviceId: myDeviceId,
      targetDeviceId: targetDevice.id,
      actionType,
      payloadJson: payloadJson ?? null,
      correlationId: correlationId ?? null,
    });

    if (!sent) {
      setError("Realtime channel is not connected");
      return false;
    }

    logEvent("info", "actions", "action-command-sent", {
      actionType,
      targetDeviceId: targetDevice.id,
      correlationId,
    });

    setSuccess("Action sent");
    return true;
  };

  const sendBlockScreenAction = async (): Promise<void> => {
    await sendActionCommand("block_screen");
  };

  const sendShutdownAction = async (): Promise<void> => {
    await sendActionCommand("shutdown");
  };

  const sendResetAction = async (): Promise<void> => {
    await sendActionCommand("reboot");
  };

  const fetchLaunchableApps = async (): Promise<void> => {
    const requestId = crypto.randomUUID();
    startPendingAction(requestId, "list_installed_apps");
    setActionMode("open-app");
    setLaunchableApps([]);
    logEvent("info", "actions", "request-launchable-apps", { requestId });
    const sent = await sendActionCommand("list_installed_apps", null, requestId);
    if (!sent) {
      clearPendingAction();
    }
  };

  const fetchRunningApps = async (): Promise<void> => {
    const requestId = crypto.randomUUID();
    startPendingAction(requestId, "list_running_apps");
    setActionMode("close-app");
    setRunningApps([]);
    logEvent("info", "actions", "request-running-apps", { requestId });
    const sent = await sendActionCommand("list_running_apps", null, requestId);
    if (!sent) {
      clearPendingAction();
    }
  };

  const openRemoteApp = async (app: LaunchableAppItem): Promise<void> => {
    const requestId = crypto.randomUUID();
    startPendingAction(requestId, "launch_app");
    const sent = await sendActionCommand("launch_app", JSON.stringify({ exec: app.exec }), requestId);
    if (!sent) {
      clearPendingAction();
    }
  };

  const closeRemoteApp = async (app: RunningAppItem): Promise<void> => {
    const requestId = crypto.randomUUID();
    startPendingAction(requestId, "close_app");
    const sent = await sendActionCommand(
      "close_app",
      JSON.stringify({ pid: app.mainPid, pids: app.pids, appName: app.name, force: false }),
      requestId,
    );
    if (!sent) {
      clearPendingAction();
    }
  };

  const refreshClipboardHistory = async (): Promise<void> => {
    if (!selectedDeviceDetail) {
      return;
    }

    if (!isAuthed || !online) {
      setClipboardHistoryMessage("No connection available");
      return;
    }

    if (selectedDeviceDetail.type === "Android") {
      setClipboardHistory([]);
      setClipboardHistoryMessage("Clipboard history for Android targets is not available due OS-level clipboard access restrictions.");
      return;
    }

    if (!selectedDeviceDetail.isOnline) {
      setClipboardHistory([]);
      setClipboardHistoryMessage("Target device must be online to fetch clipboard history.");
      return;
    }

    const requestId = crypto.randomUUID();
    setClipboardHistory([]);
    setClipboardHistoryMessage("");
    setClipboardHistoryLoading(true);
    setPendingClipboardRequestId(requestId);
    setClipboardTargetDeviceId(selectedDeviceDetail.id);

    try {
      const sent = await sendActionCommand("get_clipboard_history", null, requestId, selectedDeviceDetail.id);
      if (!sent) {
        setClipboardHistoryLoading(false);
        setPendingClipboardRequestId(null);
        setClipboardTargetDeviceId(null);
        setClipboardHistoryMessage("Failed to request clipboard history.");
      }
    } catch {
      setClipboardHistoryLoading(false);
      setPendingClipboardRequestId(null);
      setClipboardTargetDeviceId(null);
      setClipboardHistoryMessage("Failed to request clipboard history.");
    }
  };

  const copyClipboardHistoryEntry = async (value: string): Promise<void> => {
    try {
      await navigator.clipboard.writeText(value);
    } catch {
      setError("Failed to copy clipboard entry");
    }
  };

  const refreshElevationStatus = async (): Promise<void> => {
    if (!isTauriRuntime()) {
      setIsElevated(false);
      return;
    }

    const status = await getElevationStatus();
    setIsElevated(status.is_root);
  };

  const signOut = (): void => {
    tokenStore.clear();
    wsRef.current?.close();
    setIsAuthed(false);
    setProfile(null);
    setDevices([]);
    setIsElevated(false);
    setSelectedChatDeviceId("");
    setSelectedDeviceDetailId("");
    setSelectedActionDeviceId("");
    setActionMode("base");
    setLaunchableApps([]);
    setRunningApps([]);
    clearPendingAction();
    setPendingActionTimeoutMessage("");
    setClipboardHistory([]);
    setClipboardHistoryLoading(false);
    setClipboardHistoryMessage("");
    setPendingClipboardRequestId(null);
    setClipboardTargetDeviceId(null);
    setNotificationSchedules([]);
    setNotificationHistory([]);
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
        handleTabSelect("Files");
      }
    }
  }, [isAuthed, transfers]);

  if (!isAuthed) {
    return (
      <>
        {snackbar && (
          <div className={`snackbar ${snackbar.level}`} role="status" aria-live="polite">
            <span>{snackbar.message}</span>
            <button
              className="snackbar-close"
              onClick={() => setSnackbar(null)}
              type="button"
              aria-label="Dismiss message"
            >
              x
            </button>
          </div>
        )}
        <AuthView
          busy={busy}
          onLoggedIn={() => setIsAuthed(true)}
          setBusy={setBusy}
          setError={setError}
        />
      </>
    );
  }

  const onlineDevices = devices.filter((entry) => entry.isOnline && entry.isAvailable);
  const offlineDevices = devices.filter((entry) => !entry.isOnline || !entry.isAvailable);

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
        {snackbar && (
          <div className={`snackbar ${snackbar.level}`} role="status" aria-live="polite">
            <span>{snackbar.message}</span>
            <button
              className="snackbar-close"
              onClick={() => setSnackbar(null)}
              type="button"
              aria-label="Dismiss message"
            >
              x
            </button>
          </div>
        )}
        <Sidebar
          tabs={tabs}
          activeTab={activeTab}
          onSelect={(t) => {
            handleTabSelect(t as Tab);
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

        {isElevatedRuntime && (
          <section className="panel no-connection">
            <p className="no-connection-title">Full-app sudo mode detected</p>
            <p className="muted">
              Running BinaryStars as root uses a separate profile/session and may break map location and account/device sync.
              Run the app normally and authorize privileged actions only when prompted.
            </p>
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
            onUnlinkDevice={(deviceId) => void unlinkDevice(deviceId)}
            formatRam={formatRam}
            formatBattery={formatBattery}
            clipboardHistory={clipboardHistory}
            clipboardHistoryLoading={clipboardHistoryLoading}
            clipboardHistoryMessage={clipboardHistoryMessage}
            onRefreshClipboardHistory={() => void refreshClipboardHistory()}
            onCopyClipboardHistoryEntry={(value) => {
              void copyClipboardHistoryEntry(value);
            }}
          />
        )}

        {activeTab === "Files" && (
          <FilesTab
            devices={devices.map((entry) => ({ id: entry.id, name: entry.name, isOnline: entry.isOnline || entry.isBluetoothOnline === true }))}
            myDeviceId={myDeviceId}
            selectedTargetDeviceId={selectedFileTargetDeviceId}
            onSelectTargetDevice={setSelectedFileTargetDeviceId}
            onClearSent={() => void clearTransfersByScope("sent")}
            onClearReceived={() => void clearTransfersByScope("received")}
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
            myDeviceId={myDeviceId}
            chatSummaries={chatSummaries}
            chatDevice={chatDevice}
            chatMessages={chatMessages}
            selectedChatDeviceId={selectedChatDeviceId}
            newMessage={newMessage}
            onSelectChat={setSelectedChatDeviceId}
            onSetNewMessage={setNewMessage}
            onClearCurrentChat={clearCurrentChat}
            onBackToChats={() => setSelectedChatDeviceId("")}
            onSendChatMessage={() => void sendChatMessage()}
          />
        )}

        {activeTab === "Notifications" && (
          <NotificationsTab
            devices={devices.filter((d) => d.isOnline || d.id === myDeviceId)}
            selectedTargetDeviceId={notificationTargetDeviceId}
            notificationTitle={notificationTitle}
            notificationBody={notificationBody}
            scheduledForUtc={notificationScheduledFor}
            repeatMinutes={notificationRepeatMinutes}
            isScheduleEnabled={notificationScheduleEnabled}
            editingScheduleId={editingNotificationScheduleId}
            schedules={notificationSchedules}
            history={notificationHistory.filter((entry) => entry.targetDeviceId === myDeviceId)}
            onSelectTargetDevice={setNotificationTargetDeviceId}
            onSetNotificationTitle={setNotificationTitle}
            onSetNotificationBody={setNotificationBody}
            onSetScheduledForUtc={setNotificationScheduledFor}
            onSetRepeatMinutes={setNotificationRepeatMinutes}
            onSetScheduleEnabled={setNotificationScheduleEnabled}
            onSendNow={() => void sendNotificationNow()}
            onSaveSchedule={() => void saveNotificationSchedule()}
            onEditSchedule={editNotificationSchedule}
            onDeleteSchedule={(scheduleId) => {
              void deleteNotificationSchedule(scheduleId);
            }}
            onResetEditor={resetNotificationEditor}
            onDeleteHistoryItem={deleteNotificationHistoryItem}
            onClearHistory={clearNotificationHistory}
            onRefresh={() => {
              void syncNotificationsFromServer(true);
            }}
          />
        )}

        {activeTab === "Map" && (
          <MapTab
            devices={devices}
            selectedMapDevice={selectedMapDevice}
            selectedMapDeviceId={selectedMapDeviceId}
            mapFocusPoint={mapFocusPoint}
            latestPoint={latestPoint}
            history={history}
            mapDetailOpen={mapDetailOpen}
            isElevatedRuntime={isElevatedRuntime}
            locationEnabled={locationEnabled}
            locationMinutes={locationMinutes}
            geoPermissionState={geoPermissionState}
            isGeolocationAvailable={isTauriRuntime() || (typeof navigator !== "undefined" && !!navigator.geolocation)}
            lastGeoError={lastGeoError}
            lastLocationSampleAt={lastLocationSampleAt}
            lastLocationSource={lastLocationSource}
            onSelectMapDevice={setSelectedMapDeviceId}
            onSetMapDetailOpen={setMapDetailOpen}
            onRefreshMapHistory={(deviceId) => {
              void refreshMapHistory(deviceId);
            }}
            onSelectHistoryPoint={(point) => {
              setMapFocusPoint(point);
            }}
            onSetLocationEnabled={(enabled) => {
              void toggleLocationSharing(enabled);
            }}
            onSetLocationMinutes={(minutes) => {
              setLocationMinutes(minutes);
              settingsStore.setLocationMinutes(minutes);
            }}
          />
        )}

        {activeTab === "Actions" && (
          <ActionsTab
            linuxDevices={linuxActionDevices}
            selectedDeviceId={selectedActionDeviceId}
            selectedDevice={selectedActionDevice}
            onSelectDevice={setSelectedActionDeviceId}
            onSendBlockScreen={() => void sendBlockScreenAction()}
            onSendShutdown={() => void sendShutdownAction()}
            onSendReset={() => void sendResetAction()}
            onFetchLaunchableApps={() => void fetchLaunchableApps()}
            onFetchRunningApps={() => void fetchRunningApps()}
            onOpenApp={(app) => void openRemoteApp(app)}
            onCloseApp={(app) => void closeRemoteApp(app)}
            launchableApps={launchableApps}
            runningApps={runningApps}
            actionMode={actionMode}
            onBackToActions={() => setActionMode("base")}
            pendingActionType={pendingActionType}
            pendingActionRequestId={pendingActionRequestId}
            pendingActionSecondsRemaining={pendingActionSecondsRemaining}
            pendingActionTimeoutMessage={pendingActionTimeoutMessage}
            busy={busy}
          />
        )}

        {activeTab === "Settings" && (
          <SettingsTab
            profile={profile}
            devices={devices}
            themeMode={themeMode}
            logFilePath={nativeLogFilePath}
            onSetThemeMode={setThemeMode}
            onSignOut={signOut}
          />
        )}
      </section>
    </div>
  );
}

export default App;
