import type { ChatMessage, Device } from "../../types";
import type { ChatSummary } from "./types";

type Props = {
  devices: Device[];
  myDeviceId: string;
  chatSummaries: ChatSummary[];
  chatDevice: Device | null;
  chatMessages: ChatMessage[];
  selectedChatDeviceId: string;
  newMessage: string;
  onSelectChat: (deviceId: string) => void;
  onSetNewMessage: (value: string) => void;
  onClearCurrentChat: () => void;
  onBackToChats: () => void;
  onSendChatMessage: () => void;
};

export default function MessagingTab({
  devices,
  myDeviceId,
  chatSummaries,
  chatDevice,
  chatMessages,
  selectedChatDeviceId,
  newMessage,
  onSelectChat,
  onSetNewMessage,
  onClearCurrentChat,
  onBackToChats,
  onSendChatMessage,
}: Props) {
  const chatTargets = devices.filter((entry) => entry.id !== myDeviceId);

  return (
    <section className="panel-grid">
      <div className={`panel messaging-list ${selectedChatDeviceId ? "hide-on-mobile" : ""}`}>
        <div className="actions-row">
          <h3>Chats</h3>
          <select
            aria-label="Chat target"
            onChange={(event) => onSelectChat(event.target.value)}
            title="Chat target"
            value={selectedChatDeviceId}
          >
            <option value="">Choose target</option>
            {chatTargets.map((device) => (
              <option key={device.id} value={device.id}>
                {device.name} ({device.id})
              </option>
            ))}
          </select>
        </div>

        {chatSummaries.length === 0 && <p className="empty-state">No conversations yet.</p>}
        <div className="list">
          {chatSummaries.map((summary) => (
            <button
              className={`row-card ${selectedChatDeviceId === summary.deviceId ? "active" : ""}`}
              key={summary.deviceId}
              onClick={() => onSelectChat(summary.deviceId)}
              type="button"
            >
              <div className="item-head">
                <strong>{devices.find((d) => d.id === summary.deviceId)?.name ?? summary.deviceId}</strong>
                <span className="muted">{new Date(summary.lastMessage.sentAt).toLocaleTimeString()}</span>
              </div>
              <p className="muted text-truncate">{summary.lastMessage.body}</p>
            </button>
          ))}
        </div>
      </div>

      <div className={`panel chat-view ${!selectedChatDeviceId ? "hide-on-mobile" : ""}`}>
        <div className="chat-header">
          <button className="icon-btn back-btn" onClick={onBackToChats} type="button">
            ←
          </button>
          <div className="chat-info">
            <strong>{chatDevice?.name ?? "Select a chat"}</strong>
            {chatDevice && <span className="muted">{chatDevice.isOnline ? "Online" : "Offline"}</span>}
          </div>
          {selectedChatDeviceId && (
            <button className="text-btn danger" onClick={onClearCurrentChat} type="button">
              Clear
            </button>
          )}
        </div>

        <div className="messages-container">
          {!selectedChatDeviceId && <p className="empty-state">Select a device to start chatting</p>}
          {selectedChatDeviceId && chatMessages.length === 0 && <p className="empty-state">No messages yet</p>}
          {chatMessages.map((msg) => (
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
              <span className="message-time">{new Date(msg.sentAt).toLocaleTimeString()}</span>
            </div>
          ))}
        </div>

        <div className="chat-input-row">
          <textarea
            disabled={!selectedChatDeviceId}
            onChange={(e) => onSetNewMessage(e.target.value)}
            placeholder="Type a message..."
            value={newMessage}
          />
          <button className="send-btn send-text" disabled={!selectedChatDeviceId || !newMessage.trim()} onClick={onSendChatMessage} type="button">
            Send
          </button>
        </div>
      </div>
    </section>
  );
}
