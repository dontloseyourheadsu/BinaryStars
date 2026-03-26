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
        <div className="split-row">
          <h3>Chats</h3>
          <select
            aria-label="Chat target"
            onChange={(event) => onSelectChat(event.target.value)}
            title="Chat target"
            value={selectedChatDeviceId}
          >
            <option value="">Choose device</option>
            {chatTargets.map((device) => (
              <option key={device.id} value={device.id}>
                {device.name}
              </option>
            ))}
          </select>
          <button
            onClick={() => {
              if (chatTargets.length > 0) {
                const fallback = selectedChatDeviceId || chatTargets[0].id;
                onSelectChat(fallback);
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
      <div className={`panel messaging-chat ${selectedChatDeviceId ? "show-on-mobile" : "hide-chat-on-mobile"}`}>
        <div className="split-row">
          <button className="ghost mobile-only" onClick={onBackToChats} type="button">Back</button>
          <h3>{chatDevice ? chatDevice.name : "Select a chat"}</h3>
          <button className="ghost" onClick={onClearCurrentChat} type="button">Clear Chat</button>
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
          <button className="send-btn send-text" disabled={!selectedChatDeviceId || !newMessage.trim()} onClick={onSendChatMessage} type="button">
            Send
          </button>
        </div>
      </div>
    </section>
  );
}
