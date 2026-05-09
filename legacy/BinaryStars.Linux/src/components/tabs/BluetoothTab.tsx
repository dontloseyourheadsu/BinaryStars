import { useState, useEffect, useRef } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import type { ChatMessage } from "../../types";

type Props = {
  myDeviceId: string;
  allowedDeviceIds: string[];
};

export default function BluetoothTab({ myDeviceId, allowedDeviceIds }: Props) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [serverRunning, setServerRunning] = useState(false);
  const [connectedDeviceId, setConnectedDeviceId] = useState<string | null>(null);
  const [newMessage, setNewMessage] = useState("");
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  useEffect(() => {
    // Initial status check
    void invoke<any>("get_bluetooth_status").then(status => {
      setServerRunning(status.serverRunning);
      setConnectedDeviceId(status.connectedDeviceId);
    });

    const unlistenStatus = listen("bluetooth-status", (event: any) => {
      setConnectedDeviceId(event.payload.deviceId);
      if (!event.payload.connected) {
        setMessages([]); 
      }
    });

    const unlistenMsg = listen("bluetooth-message", (event: any) => {
      const msg = event.payload;
      const localMsg: ChatMessage = {
        id: msg.id || crypto.randomUUID(),
        deviceId: msg.sender || "Peer",
        senderDeviceId: msg.sender || "Peer",
        body: msg.content,
        sentAt: Date.now(),
        isOutgoing: false,
      };
      setMessages((prev) => [...prev, localMsg]);
    });

    return () => {
      unlistenStatus.then(fn => fn());
      unlistenMsg.then(fn => fn());
    };
  }, []);

  const handleStartServer = async () => {
    try {
      await invoke("start_bluetooth_server", { myDeviceId, allowedDeviceIds });
      setServerRunning(true);
    } catch (e) {
      console.error(e);
    }
  };

  const handleStopServer = async () => {
    try {
      await invoke("stop_bluetooth_server");
      setServerRunning(false);
      setConnectedDeviceId(null);
      setMessages([]);
    } catch (e) {
      console.error(e);
    }
  };

  const handleSendMessage = async () => {
    if (!newMessage.trim()) return;
    try {
      await invoke("send_bluetooth_message", { content: newMessage.trim() });
      const localMsg: ChatMessage = {
        id: crypto.randomUUID(),
        deviceId: connectedDeviceId || "Peer",
        senderDeviceId: "Me",
        body: newMessage.trim(),
        sentAt: Date.now(),
        isOutgoing: true,
      };
      setMessages(prev => [...prev, localMsg]);
      setNewMessage("");
    } catch (e) {
      console.error(e);
    }
  };

  const handleSendFile = () => {
     const input = document.createElement("input");
     input.type = "file";
     input.onchange = async (e: any) => {
       const file = e.target.files?.[0];
       if (!file) return;
       const reader = new FileReader();
       reader.onload = async () => {
         const result = reader.result as string;
         const base64 = result.split(",")[1];
         try {
           await invoke("send_bluetooth_file", { name: file.name, base64Data: base64 });
           const localMsg: ChatMessage = {
             id: crypto.randomUUID(),
             deviceId: connectedDeviceId || "Peer",
             senderDeviceId: "Me",
             body: `Sent file: ${file.name}`,
             sentAt: Date.now(),
             isOutgoing: true,
           };
           setMessages(prev => [...prev, localMsg]);
         } catch (err) {
           console.error(err);
         }
       };
       reader.readAsDataURL(file);
     };
     input.click();
  };

  const handleDownloadFile = async (msg: ChatMessage) => {
     const match = msg.body.match(/Received file: (.*)/);
     if (match) {
        const fileName = match[1];
        try {
          const path = await invoke<string>("download_bluetooth_file", { fileName });
          alert(`File saved to ${path}`);
          setMessages(prev => prev.filter(m => m.id !== msg.id));
        } catch (e) {
          alert(String(e));
        }
     }
  };

  return (
    <section className="panel-grid">
      <div className="panel messaging-list">
        <div className="actions-row">
          <h3>Bluetooth Session</h3>
          {serverRunning ? (
            <button className="text-btn danger" onClick={handleStopServer} type="button">Stop</button>
          ) : (
            <button className="text-btn" onClick={handleStartServer} type="button">Start Server</button>
          )}
        </div>
        
        <div className="list">
           <article className="row-card static">
              <div className="item-head">
                <span className={`status-dot ${serverRunning ? 'online' : 'offline'}`}></span>
                <strong>Status</strong>
              </div>
              <p className="muted">
                {serverRunning ? (connectedDeviceId ? `Connected to ${connectedDeviceId}` : "Waiting for phone...") : "Server is stopped"}
              </p>
           </article>

           <div style={{ padding: '1rem' }}>
              <p className="muted" style={{ fontSize: '0.85rem' }}>
                This is a transient sandbox. Data is NOT saved to the cloud or local disk. 
                Sessions are wiped automatically on disconnect or app close.
              </p>
           </div>
           
           {connectedDeviceId && (
              <div className="actions-row" style={{ padding: '0 1rem' }}>
                 <button onClick={handleSendFile} type="button" style={{ width: '100%' }}>Send File</button>
              </div>
           )}
        </div>
      </div>

      <div className="panel chat-view">
        <div className="chat-header">
           <div className="chat-info">
             <strong>{connectedDeviceId ? `Chat with ${connectedDeviceId}` : "No Active Peer"}</strong>
             <span className="muted">{connectedDeviceId ? "Direct P2P Link" : "Waiting for connection"}</span>
           </div>
        </div>

        <div className="messages-container">
          {messages.length === 0 && <p className="empty-state">No messages in this session</p>}
          {messages.map((msg) => (
            <div className={`message-bubble ${msg.isOutgoing ? "outgoing" : "incoming"}`} key={msg.id}>
              <button 
                className="copy-msg-btn" 
                onClick={() => navigator.clipboard.writeText(msg.body)}
                title="Copy message"
                type="button"
              >
                📋
              </button>
              <p>{msg.body}</p>
              {msg.body.includes("Received file:") && !msg.isOutgoing && (
                <button className="text-btn" onClick={() => handleDownloadFile(msg)} type="button" style={{ marginTop: '0.5rem', color: 'var(--primary-purple)' }}>
                   Download File
                </button>
              )}
              <span className="message-time">{new Date(msg.sentAt).toLocaleTimeString()}</span>
            </div>
          ))}
          <div ref={messagesEndRef} />
        </div>

        <div className="chat-input-row">
          <textarea
            disabled={!connectedDeviceId}
            onChange={(e) => setNewMessage(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && !e.shiftKey && (e.preventDefault(), handleSendMessage())}
            placeholder={connectedDeviceId ? "Type a transient message..." : "Connect a device first"}
            value={newMessage}
          />
          <button className="send-btn send-text" disabled={!connectedDeviceId || !newMessage.trim()} onClick={handleSendMessage} type="button">
            Send
          </button>
        </div>
      </div>
    </section>
  );
}
