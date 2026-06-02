import React, { useState, useRef, useEffect } from "react";
import { listen } from "@tauri-apps/api/event";
import { BluetoothMessage, sendBluetoothMessage, sendBluetoothFile, downloadBluetoothFile, saveFileToCustomPath, getMessagesPaged } from "./chatApi";
import "../../features/features.css";

interface ChatPanelProps {
  isDark: boolean;
  peerId: string;
  onDisconnect: () => void;
}

export const ChatPanel: React.FC<ChatPanelProps> = ({
  isDark,
  peerId,
  onDisconnect,
}) => {
  const [messages, setMessages] = useState<BluetoothMessage[]>([]);
  const [text, setText] = useState("");
  const [downloading, setDownloading] = useState<{ [fileName: string]: boolean }>({});
  const [downloadPaths, setDownloadPaths] = useState<{ [fileName: string]: string }>({});
  
  // Pagination & Lazy Loading states
  const [offset, setOffset] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [isLoadingHistory, setIsLoadingHistory] = useState(false);
  
  const feedRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const shouldAutoScrollRef = useRef(true);

  // 1. Initial Page Load and Listener registration
  useEffect(() => {
    setMessages([]);
    setOffset(0);
    setHasMore(true);
    shouldAutoScrollRef.current = true;

    // Load initial 20 messages
    getMessagesPaged(peerId, 20, 0)
      .then((history) => {
        setMessages(history);
        if (history.length < 20) {
          setHasMore(false);
        }
      })
      .catch(console.error);

    // Listen to real-time messages
    const unlistenMsgPromise = listen("bluetooth-message", (event) => {
      const msg = event.payload as BluetoothMessage;
      setMessages((prev) => {
        if (prev.some((m) => m.id === msg.id)) return prev;
        return [...prev, msg];
      });
      shouldAutoScrollRef.current = true;
    });

    return () => {
      unlistenMsgPromise.then((unlisten) => unlisten());
    };
  }, [peerId]);

  // 2. Auto scroll to bottom
  useEffect(() => {
    if (shouldAutoScrollRef.current && feedRef.current) {
      feedRef.current.scrollTop = feedRef.current.scrollHeight;
    }
  }, [messages]);

  // 3. Lazy Loading older history on scrolling to top
  const handleScroll = async (e: React.UIEvent<HTMLDivElement>) => {
    const target = e.currentTarget;
    if (target.scrollTop === 0 && hasMore && !isLoadingHistory) {
      setIsLoadingHistory(true);
      shouldAutoScrollRef.current = false;
      const nextOffset = offset + 20;

      try {
        const history = await getMessagesPaged(peerId, 20, nextOffset);
        if (history.length > 0) {
          const oldScrollHeight = target.scrollHeight;
          
          setMessages((prev) => {
            const existingIds = new Set(prev.map((m) => m.id));
            const filteredHistory = history.filter((m) => !existingIds.has(m.id));
            return [...filteredHistory, ...prev];
          });
          
          setOffset(nextOffset);
          if (history.length < 20) {
            setHasMore(false);
          }

          // Restore scroll position
          setTimeout(() => {
            target.scrollTop = target.scrollHeight - oldScrollHeight;
          }, 0);
        } else {
          setHasMore(false);
        }
      } catch (err) {
        console.error("Failed to load historical messages", err);
      } finally {
        setIsLoadingHistory(false);
      }
    }
  };

  const handleSendText = async () => {
    if (!text.trim()) return;
    try {
      shouldAutoScrollRef.current = true;
      await sendBluetoothMessage(text.trim());
      setText("");
    } catch (e: any) {
      alert(`Send failed: ${e}`);
    }
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;
    const file = files[0];
    
    const reader = new FileReader();
    reader.onload = async () => {
      try {
        const arrayBuffer = reader.result as ArrayBuffer;
        const bytes = new Uint8Array(arrayBuffer);
        
        let binary = "";
        const len = bytes.byteLength;
        for (let i = 0; i < len; i++) {
          binary += String.fromCharCode(bytes[i]);
        }
        const base64Data = window.btoa(binary);
        
        shouldAutoScrollRef.current = true;
        await sendBluetoothFile(file.name, base64Data);
      } catch (err: any) {
        alert(`File send error: ${err}`);
      }
    };
    reader.readAsArrayBuffer(file);
    
    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  const handleDownload = async (msgId: string) => {
    setDownloading((prev) => ({ ...prev, [msgId]: true }));
    try {
      const path = await downloadBluetoothFile(msgId);
      setDownloadPaths((prev) => ({ ...prev, [msgId]: path }));
    } catch (err: any) {
      alert(`Download failed: ${err}`);
    } finally {
      setDownloading((prev) => ({ ...prev, [msgId]: false }));
    }
  };

  const handleSaveAs = async (msgId: string) => {
    try {
      const path = await saveFileToCustomPath(msgId);
      if (path) {
        setDownloadPaths((prev) => ({ ...prev, [msgId]: path }));
        alert(`File saved to: ${path}`);
      }
    } catch (err: any) {
      alert(`Save failed: ${err}`);
    }
  };

  return (
    <div className="chat-container">
      {/* Chat Header */}
      <div className={`chat-header ${isDark ? "dark" : "light"}`}>
        <div className="peer-info">
          <span className="online-indicator"></span>
          <div>
            <span className="header-label">Connected Peer</span>
            <h4 className="peer-title">{peerId}</h4>
          </div>
        </div>
        <button onClick={onDisconnect} className="btn-disconnect">
          Disconnect
        </button>
      </div>

      {/* Message Feed with lazy scroll */}
      <div 
        ref={feedRef} 
        onScroll={handleScroll} 
        className="message-feed"
      >
        {isLoadingHistory && (
          <div style={{ textAlign: "center", padding: "8px", fontSize: "11px", color: "var(--text-secondary-dark)" }}>
            Loading cosmic history...
          </div>
        )}
        
        {messages.map((msg) => {
          const isOutgoing = msg.sender === "Me";
          const resolvedPath = downloadPaths[msg.id] || msg.filePath || "";
          
          return (
            <div
              key={msg.id}
              className={`message-bubble-wrapper ${isOutgoing ? "outgoing" : "incoming"}`}
            >
              <div
                className={`message-bubble ${
                  isOutgoing
                    ? (isDark ? "bubble-outgoing-dark" : "bubble-outgoing-light")
                    : (isDark ? "bubble-incoming-dark" : "bubble-incoming-light")
                }`}
              >
                {msg.isFile ? (
                  <div className="file-message" style={{ flexDirection: "column", alignItems: "flex-start", gap: "6px" }}>
                    <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                      <span className="file-icon">📁</span>
                      <div className="file-details">
                        <span className="file-name">{msg.fileName}</span>
                        <span className="file-meta">
                          {isOutgoing ? "Sent File" : "Received File"}
                        </span>
                        {resolvedPath && (
                          <span className="file-path">
                            Saved: {resolvedPath}
                          </span>
                        )}
                      </div>
                    </div>
                    <div className="file-actions" style={{ display: "flex", gap: "6px", marginTop: "4px" }}>
                      {!resolvedPath && (
                        <button
                          onClick={() => handleDownload(msg.id)}
                          disabled={downloading[msg.id]}
                          className="btn-download-flat"
                          style={{
                            background: "transparent",
                            border: "1px solid rgba(255, 255, 255, 0.2)",
                            color: "inherit",
                            fontSize: "10px",
                            padding: "2px 6px",
                            borderRadius: "4px",
                            cursor: "pointer"
                          }}
                        >
                          {downloading[msg.id] ? "..." : "⬇️ Download"}
                        </button>
                      )}
                      <button
                        onClick={() => handleSaveAs(msg.id)}
                        className="btn-save-as-flat"
                        style={{
                          background: "transparent",
                          border: "1px solid rgba(255, 255, 255, 0.2)",
                          color: "inherit",
                          fontSize: "10px",
                          padding: "2px 6px",
                          borderRadius: "4px",
                          cursor: "pointer"
                        }}
                      >
                        📂 Save As...
                      </button>
                    </div>
                  </div>
                ) : (
                  <p className="message-text">{msg.content}</p>
                )}
              </div>
              <span className="message-time">
                {new Date(msg.sentAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
              </span>
            </div>
          );
        })}
      </div>

      {/* Input Tray */}
      <div className="input-tray">
        <input
          type="file"
          ref={fileInputRef}
          onChange={handleFileChange}
          style={{ display: "none" }}
        />
        <button
          onClick={() => fileInputRef.current?.click()}
          className={`btn-attach ${isDark ? "dark" : "light"}`}
        >
          📎
        </button>
        <input
          type="text"
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && handleSendText()}
          placeholder="Type a message..."
          className={`text-input ${isDark ? "dark" : "light"}`}
        />
        <button
          onClick={handleSendText}
          disabled={!text.trim()}
          className={`btn-send ${text.trim() ? "active" : ""}`}
        >
          🚀
        </button>
      </div>
    </div>
  );
};
