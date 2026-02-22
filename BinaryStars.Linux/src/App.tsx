import { ChangeEvent, useEffect, useMemo, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet';
import { Icon } from 'leaflet';
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';
import "./App.css";
import { api, tokenStore } from "./api";
import { getDeviceId, getDeviceName, setDeviceName } from "./device";
import { getLocalDeviceInfo } from "./tauriDeviceInfo";
import { cacheStore, settingsStore } from "./storage";
import type {
  AccountProfile,
  ChatMessage,
  Device,
  FileTransfer,
  LocationPoint,
  MessageDto,
  Note,
} from "./types";

type Tab = "Devices" | "Files" | "Notes" | "Messaging" | "Map" | "Settings";

const tabs: Tab[] = ["Devices", "Files", "Notes", "Messaging", "Map", "Settings"];
import AuthView from "./components/Auth/AuthView";
import Sidebar from "./components/UI/Sidebar";
import { formatSize, statusLabel, toWsUrl, upsertMessage } from "./utils/helpers";

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

  const [devices, setDevices] = useState<Device[]>([]);
  const [transfers, setTransfers] = useState<FileTransfer[]>(cacheStore.getTransfers());
  const [notes, setNotes] = useState<Note[]>(cacheStore.getNotes());
  const [messages, setMessages] = useState<ChatMessage[]>(cacheStore.getMessages());
  const [profile, setProfile] = useState<AccountProfile | null>(null);
  const [history, setHistory] = useState<LocationPoint[]>([]);
  const [selectedMapDeviceId, setSelectedMapDeviceId] = useState("");
  const [selectedChatDeviceId, setSelectedChatDeviceId] = useState("");
  const [newMessage, setNewMessage] = useState("");
  const [noteName, setNoteName] = useState("");
  const [noteContent, setNoteContent] = useState("");
  const [noteType, setNoteType] = useState<"Plaintext" | "Markdown">("Plaintext");
  const [editingNoteId, setEditingNoteId] = useState<string | null>(null);
  const [deviceAlias, setDeviceAlias] = useState(getDeviceName());
  const [isDark, setIsDark] = useState(settingsStore.getDarkMode(false));
  const [locationEnabled, setLocationEnabled] = useState(settingsStore.getLocationEnabled(false));
  const [locationMinutes, setLocationMinutes] = useState(settingsStore.getLocationMinutes(15));
  const [localMemoryLoadPercent, setLocalMemoryLoadPercent] = useState<number | null>(null);
  const [mapDetailOpen, setMapDetailOpen] = useState(false);

  const wsRef = useRef<WebSocket | null>(null);
  const filePickerRef = useRef<HTMLInputElement | null>(null);
  const noteContentRef = useRef<HTMLTextAreaElement | null>(null);
  const myDeviceId = getDeviceId();

  useEffect(() => {
    document.documentElement.dataset.theme = isDark ? "dark" : "light";
    settingsStore.setDarkMode(isDark);
  }, [isDark]);

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

  const refreshProfile = async (): Promise<void> => {
    const next = await api.getProfile();
    setProfile(next);
  };

  const refreshDevices = async (): Promise<void> => {
    const next = await api.getDevices();
    setDevices(next);
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

  const refreshMapHistory = async (deviceId: string): Promise<void> => {
    const next = await api.getLocationHistory(deviceId);
    setHistory(next);
  };

  const initSession = async (): Promise<void> => {
    try {
      setBusy(true);
      await Promise.all([refreshProfile(), refreshDevices(), refreshNotes(), refreshTransfers()]);
      setError("");
      setIsAuthed(true);
    } catch (nextError) {
      tokenStore.clear();
      setIsAuthed(false);
      setError(nextError instanceof Error ? nextError.message : "Failed to load account session");
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
        if (envelope.type.toLowerCase() !== "message") {
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

  useEffect(() => {
    if (!isAuthed || !locationEnabled) {
      return;
    }
    settingsStore.setLocationEnabled(locationEnabled);
    settingsStore.setLocationMinutes(locationMinutes);

    const run = () => {
      navigator.geolocation.getCurrentPosition(
        async (position) => {
          try {
            await api.sendLocation({
              deviceId: myDeviceId,
              latitude: position.coords.latitude,
              longitude: position.coords.longitude,
              accuracyMeters: position.coords.accuracy,
              recordedAt: new Date().toISOString(),
            });
          } catch {
            // noop
          }
        },
        () => {
          // noop
        },
        { enableHighAccuracy: true },
      );
    };

    run();
    const timer = window.setInterval(run, locationMinutes * 60_000);
    return () => window.clearInterval(timer);
  }, [isAuthed, locationEnabled, locationMinutes, myDeviceId]);

  useEffect(() => {
    if (!selectedMapDeviceId || !isAuthed) {
      return;
    }
    void refreshMapHistory(selectedMapDeviceId);
  }, [isAuthed, selectedMapDeviceId]);

  useEffect(() => {
    if (!selectedMapDeviceId) {
      setMapDetailOpen(false);
    }
  }, [selectedMapDeviceId]);

  const linkCurrentDevice = async (): Promise<void> => {
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
    await api.unlinkDevice(myDeviceId);
    await refreshDevices();
  };

  const sendFile = async (file: File, targetDeviceId: string): Promise<void> => {
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
    await api.rejectTransfer(transfer.id, myDeviceId);
    await refreshTransfers();
  };

  const saveNote = async (): Promise<void> => {
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
    setHistory([]);
    setDrawerOpen(false);
  };

  const currentDevice = devices.find((entry) => entry.id === myDeviceId) ?? null;

  useEffect(() => {
    if (!isAuthed || !currentDevice) {
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
  }, [isAuthed, currentDevice, myDeviceId]);

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

  const onlineDevices = devices.filter((entry) => entry.isOnline);
  const offlineDevices = devices.filter((entry) => !entry.isOnline);

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
            <p className="no-connection-title">No connection</p>
            <p className="muted">Connect to the internet to load data.</p>
            <button onClick={() => void initSession()} type="button">Retry</button>
          </section>
        )}

        {online && activeTab === "Devices" && (
          <section className="panel-stack">
            <div className="panel">
              <p className="section-label">Online — {onlineDevices.length}</p>
              {onlineDevices.length === 0 && <p className="muted">No devices online.</p>}
              <div className="list">
                {onlineDevices.map((device) => (
                  <button className="row-card" key={device.id} onClick={() => openChat(device.id)} type="button">
                    <div className="item-head">
                      <span className={`status-dot ${device.isOnline ? "online" : "offline"}`} />
                      <strong>{device.name}</strong>
                    </div>
                    <span className="muted">{device.type} • {device.ipAddress}</span>
                    <span className="muted">
                      CPU {device.cpuLoadPercent ?? 0}% · ↑ {device.wifiUploadSpeed} · ↓ {device.wifiDownloadSpeed}
                    </span>
                    <span className="muted">
                      RAM {formatRam(device)} · Battery {formatBattery(device)}
                    </span>
                  </button>
                ))}
              </div>
            </div>
            <div className="panel">
              <p className="section-label">Offline — {offlineDevices.length}</p>
              {offlineDevices.length === 0 && <p className="muted">No offline devices.</p>}
              <div className="list">
                {offlineDevices.map((device) => (
                  <button className="row-card" key={device.id} onClick={() => openChat(device.id)} type="button">
                    <div className="item-head">
                      <span className={`status-dot ${device.isOnline ? "online" : "offline"}`} />
                      <strong>{device.name}</strong>
                    </div>
                    <span className="muted">{device.type} • Last seen {new Date(device.lastSeen).toLocaleString()}</span>
                    <span className="muted">
                      CPU {device.cpuLoadPercent ?? 0}% · ↑ {device.wifiUploadSpeed} · ↓ {device.wifiDownloadSpeed}
                    </span>
                    <span className="muted">
                      RAM {formatRam(device)} · Battery {formatBattery(device)}
                    </span>
                  </button>
                ))}
              </div>
              <div className="split-row">
                <input
                  aria-label="Device name"
                  placeholder="Device name"
                  value={deviceAlias}
                  onChange={(event) => {
                    const value = event.target.value;
                    setDeviceAlias(value);
                    setDeviceName(value);
                  }}
                />
                {currentDevice ? (
                  <button onClick={() => void unlinkCurrentDevice()} type="button">
                    Unlink This Device
                  </button>
                ) : (
                  <button onClick={() => void linkCurrentDevice()} type="button">
                    Link This Device
                  </button>
                )}
              </div>
            </div>
          </section>
        )}

        {online && activeTab === "Files" && (
          <section className="panel-stack">
            <div className="panel">
              <div className="split-row">
                <h3>File Transfers</h3>
                <button
                  onClick={() => filePickerRef.current?.click()}
                  type="button"
                >
                  Send
                </button>
              </div>
              <input hidden onChange={(event) => void onPickFile(event)} ref={filePickerRef} type="file" />
              {transfers.length === 0 && <p className="empty-state">No transfers yet.</p>}
              <div className="list">
                {transfers.map((transfer) => (
                  <article className="row-card static" key={transfer.id}>
                    <strong>{transfer.fileName}</strong>
                    <span className="muted">
                      {formatSize(transfer.sizeBytes)} · {statusLabel(transfer.status, transfer.isSender)}
                    </span>
                    <div className="chip-row">
                      {!transfer.isSender && transfer.status === "Available" && (
                        <button onClick={() => void downloadTransfer(transfer)} type="button">
                          Download
                        </button>
                      )}
                      {!transfer.isSender && transfer.status === "Available" && (
                        <button className="ghost" onClick={() => void rejectTransfer(transfer)} type="button">
                          Reject
                        </button>
                      )}
                    </div>
                  </article>
                ))}
              </div>
            </div>
          </section>
        )}

        {online && activeTab === "Notes" && (
          <section className="panel-grid">
            <div className="panel">
              <div className="split-row">
                <h3>Notes</h3>
                <button
                  onClick={() => {
                    setEditingNoteId(null);
                    setNoteName("");
                    setNoteContent("");
                  }}
                  type="button"
                >
                  + New
                </button>
              </div>
              <div className="list">
                {notes.length === 0 && <p className="empty-state">No notes yet. Create one to get started!</p>}
                {notes.map((note) => (
                  <article className="row-card static" key={note.id}>
                    <button className="link-row" onClick={() => openNote(note)} type="button">
                      <strong>{note.name}</strong>
                      <span className="muted">{note.contentType} · {new Date(note.updatedAt).toLocaleString()}</span>
                    </button>
                    <button className="ghost" onClick={() => void deleteNote(note.id)} type="button">
                      Delete
                    </button>
                  </article>
                ))}
              </div>
            </div>
            <div className="panel">
              <h3>{editingNoteId ? "Edit Note" : "Create Note"}</h3>
              <label>
                Name
                <input value={noteName} onChange={(event) => setNoteName(event.target.value)} />
              </label>
              <label>
                Type
                <select
                  onChange={(event) => setNoteType(event.target.value as "Plaintext" | "Markdown")}
                  value={noteType}
                >
                  <option value="Plaintext">Plaintext</option>
                  <option value="Markdown">Markdown</option>
                </select>
              </label>
              <label>
                Content
                <div className="md-toolbar">
                  <button className="editor-icon" type="button" onClick={() => wrapSelection('**')}><b>B</b></button>
                  <button className="editor-icon" type="button" onClick={() => wrapSelection('*')}><i>I</i></button>
                  <button className="editor-icon" type="button" onClick={() => wrapSelection('__')}>U</button>
                  <button className="editor-icon" type="button" onClick={() => wrapSelection('~~')}>S</button>
                  <button className="editor-icon" type="button" onClick={() => wrapSelection('# ', '')}>H1</button>
                  <button className="editor-icon" type="button" onClick={() => wrapSelection('## ', '')}>H2</button>
                  <button className="editor-icon" type="button" onClick={() => wrapSelection('### ', '')}>H3</button>
                  <button className="editor-icon" type="button" onClick={() => insertAtSelection('- item\n')}>•</button>
                  <button className="editor-icon" type="button" onClick={() => insertAtSelection('1. item\n')}>1.</button>
                  <button className="editor-icon" type="button" onClick={() => insertAtSelection('- [ ] task\n')}>☑</button>
                  <button className="editor-icon" type="button" onClick={() => wrapSelection('```\n', '\n```')}>{'{ }'}</button>
                  <button className="editor-icon" type="button" onClick={() => wrapSelection('`')}>`</button>
                  <button className="editor-icon" type="button" onClick={() => wrapSelection('> ', '')}>❝</button>
                  <button className="editor-icon" type="button" onClick={() => wrapSelection('[', '](url)')}>🔗</button>
                  <button className="editor-icon" type="button" onClick={() => insertAtSelection('\n---\n')}>—</button>
                </div>
                <textarea ref={noteContentRef} onChange={(event) => setNoteContent(event.target.value)} rows={10} value={noteContent} />
              </label>
              <div className="split-row">
                <button
                  className="ghost"
                  onClick={() => {
                    setEditingNoteId(null);
                    setNoteName("");
                    setNoteContent("");
                  }}
                  type="button"
                >
                  Cancel
                </button>
                <button onClick={() => void saveNote()} type="button">
                  Save
                </button>
              </div>
              {noteType === "Markdown" && (
                <div className="markdown-preview">
                  <ReactMarkdown>{noteContent || "_Preview_"}</ReactMarkdown>
                </div>
              )}
            </div>
          </section>
        )}

        {online && activeTab === "Messaging" && (
          <section className="panel-grid">
            <div className="panel">
              <div className="split-row">
                <h3>Chats</h3>
                <button
                  onClick={() => {
                    if (devices.length > 0) {
                      setSelectedChatDeviceId(devices[0].id);
                    }
                  }}
                  type="button"
                >
                  New
                </button>
              </div>
              <div className="list">
                {chatSummaries.length === 0 && <p className="empty-state">No chats yet. Start one to send a message.</p>}
                {chatSummaries.map((summary) => (
                  <button
                    className={`row-card ${summary.deviceId === selectedChatDeviceId ? "active" : ""}`}
                    key={summary.deviceId}
                    onClick={() => setSelectedChatDeviceId(summary.deviceId)}
                    type="button"
                  >
                    <strong>{summary.name}</strong>
                    <span className="muted">{summary.lastMessage.body}</span>
                  </button>
                ))}
              </div>
            </div>
            <div className="panel">
              <div className="split-row">
                <h3>{chatDevice ? chatDevice.name : "Select a chat"}</h3>
                <button className="ghost" onClick={clearCurrentChat} type="button">Clear</button>
              </div>
              <div className="chat-box">
                {chatMessages.map((message) => (
                  <div className={`bubble ${message.isOutgoing ? "out" : "in"}`} key={message.id}>
                    <p>{message.body}</p>
                    <small>{new Date(message.sentAt).toLocaleTimeString()}</small>
                  </div>
                ))}
              </div>
              <div className="split-row">
                <input
                  onChange={(event) => setNewMessage(event.target.value)}
                  placeholder="Write a message"
                  value={newMessage}
                />
                <button className="send-btn" disabled={!selectedChatDeviceId} onClick={() => void sendChatMessage()} type="button">
                  ➤
                </button>
              </div>
            </div>
          </section>
        )}

        {online && activeTab === "Map" && (
          <section className="panel-grid">
            {!mapDetailOpen && (
            <div className="panel">
              <h3>Devices</h3>
              <div className="list">
                {devices.map((device) => (
                  <button
                    className={`row-card ${selectedMapDeviceId === device.id ? "active" : ""}`}
                    key={device.id}
                    onClick={() => {
                      setSelectedMapDeviceId(device.id);
                      setMapDetailOpen(true);
                    }}
                    type="button"
                  >
                    <strong>{device.name}</strong>
                    <span className="muted">{device.isOnline ? "Online" : "Offline"}</span>
                  </button>
                ))}
              </div>
              {devices.length === 0 && <p className="empty-state">No devices available</p>}
            </div>
            )}
            {mapDetailOpen && (
            <div className="panel">
              <div className="split-row">
                <button className="ghost" onClick={() => setMapDetailOpen(false)} type="button">Back</button>
                <h3>{selectedMapDevice?.name ?? "Map"}</h3>
                <button onClick={() => selectedMapDeviceId && void refreshMapHistory(selectedMapDeviceId)} type="button">Live</button>
              </div>
              <div className="map-wrap">
                {latestPoint ? (
                  <MapContainer center={[latestPoint.latitude, latestPoint.longitude]} zoom={13} style={{ height: '100%', width: '100%' }}>
                    <TileLayer
                      attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                      url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                    />
                    <Marker position={[latestPoint.latitude, latestPoint.longitude]}>
                      <Popup>{selectedMapDevice?.name ?? 'Device'}</Popup>
                    </Marker>
                  </MapContainer>
                ) : (
                  <div className="map-empty">No location history available</div>
                )}
              </div>
              <p className="section-label">Location History</p>
              <div className="list compact">
                {history.map((point) => (
                  <article className="row-card static" key={point.id}>
                    <strong>{point.title}</strong>
                    <span className="muted">
                      {point.latitude.toFixed(5)}, {point.longitude.toFixed(5)} · {new Date(point.recordedAt).toLocaleString()}
                    </span>
                  </article>
                ))}
              </div>
              <p className="section-label">Location Updates</p>
              <div className="panel inset-panel">
                <label className="inline">
                  Share location in background
                  <input
                    checked={locationEnabled}
                    onChange={(event) => {
                      const enabled = event.target.checked;
                      setLocationEnabled(enabled);
                      settingsStore.setLocationEnabled(enabled);
                    }}
                    type="checkbox"
                  />
                </label>
                <select
                  aria-label="Location update interval"
                  onChange={(event) => {
                    const minutes = Number(event.target.value);
                    setLocationMinutes(minutes);
                    settingsStore.setLocationMinutes(minutes);
                  }}
                  value={locationMinutes}
                >
                  <option value={15}>15 minutes</option>
                  <option value={30}>30 minutes</option>
                  <option value={60}>60 minutes</option>
                </select>
              </div>
            </div>
            )}
          </section>
        )}

        {online && activeTab === "Settings" && (
          <section className="panel-grid">
            <div className="panel">
              <p className="section-label">Account</p>
              <p>
                <strong>{profile?.username ?? "Unknown"}</strong>
              </p>
              <p className="muted">{profile?.email ?? "Unknown"}</p>
              <p className="muted">Plan: <span className="plan-badge">{profile?.role ?? "Unknown"}</span></p>
              <button className="ghost" type="button">Upgrade</button>
              <button onClick={signOut} type="button">
                Sign out
              </button>
            </div>
            <div className="panel">
              <p className="section-label">Appearance</p>
              <label className="inline">
                Dark mode
                <input checked={isDark} onChange={(event) => setIsDark(event.target.checked)} type="checkbox" />
              </label>
              <h3>Connected devices ({devices.length})</h3>
              <div className="list compact">
                {devices.map((device) => (
                  <article className="row-card static" key={device.id}>
                    <strong>{device.name}</strong>
                    <span className="muted">{device.id}</span>
                  </article>
                ))}
              </div>
            </div>
          </section>
        )}
      </section>
    </div>
  );
}

export default App;
