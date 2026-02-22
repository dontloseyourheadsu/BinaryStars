import type { ChatMessage, Device } from "../../types";
import type { ChatSummary } from "./types";

type Props = {
  devices: Device[];
  chatSummaries: ChatSummary[];
  chatDevice: Device | null;
  chatMessages: ChatMessage[];
  selectedChatDeviceId: string;
  newMessage: string;
  onSelectChat: (deviceId: string) => void;
  onSetNewMessage: (value: string) => void;
  onClearCurrentChat: () => void;
  onSendChatMessage: () => void;
};

export default function MessagingTab({
  devices,
  chatSummaries,
  chatDevice,
  chatMessages,
  selectedChatDeviceId,
  newMessage,
  onSelectChat,
  onSetNewMessage,
  onClearCurrentChat,
  onSendChatMessage,
}: Props) {
  return (
    <section className="panel-grid">
      <div className="panel">
        <div className="split-row">
          <h3>Chats</h3>
          <button
            onClick={() => {
              if (devices.length > 0) {
                onSelectChat(devices[0].id);
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
              onClick={() => onSelectChat(summary.deviceId)}
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
          <button className="ghost" onClick={onClearCurrentChat} type="button">Clear</button>
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
            onChange={(event) => onSetNewMessage(event.target.value)}
            placeholder="Write a message"
            value={newMessage}
          />
          <button className="send-btn" disabled={!selectedChatDeviceId} onClick={onSendChatMessage} type="button">
            ➤
          </button>
        </div>
      </div>
    </section>
  );
}
