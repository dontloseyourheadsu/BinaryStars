import type { ReactNode } from "react";

type Props = {
  tabs: string[];
  activeTab: string;
  onSelect: (tab: string) => void;
  drawerOpen: boolean;
};

const iconMap: Record<string, ReactNode> = {
  Devices: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M4 5h16a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1h-6l1.5 2H18v1H6v-1h2.5L10 17H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Zm0 2v8h16V7H4Z" fill="currentColor" />
    </svg>
  ),
  Files: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M4 5h6l2 2h8a1 1 0 0 1 1 1v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 1-2Zm1 3v10h14V9h-8.8l-2-2H5Z" fill="currentColor" />
    </svg>
  ),
  Notes: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M6 3h9l5 5v12a1 1 0 0 1-1 1H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Zm8 1.5V9h4.5L14 4.5ZM8 12h8v1.5H8V12Zm0 3h8v1.5H8V15Zm0-6h5v1.5H8V9Z" fill="currentColor" />
    </svg>
  ),
  Messaging: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M4 5h16a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H8l-4 3v-3H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Zm1 2v7h14V7H5Z" fill="currentColor" />
    </svg>
  ),
  Notifications: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 3a6 6 0 0 1 6 6v3.7l1.5 2.6a1 1 0 0 1-.87 1.5H5.37a1 1 0 0 1-.87-1.5L6 12.7V9a6 6 0 0 1 6-6Zm0 2a4 4 0 0 0-4 4v4l-1.1 2h10.2L16 13V9a4 4 0 0 0-4-4Zm-2 13h4a2 2 0 0 1-4 0Z" fill="currentColor" />
    </svg>
  ),
  Map: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M15 4l5-2v16l-5 2-6-2-5 2V4l5-2 6 2Zm-1 2.2-4-1.33V17.8l4 1.33V6.2Zm-9-.87v12.34l3-1.2V4.13l-3 1.2Zm14 11.34V4.33l-3 1.2v12.34l3-1.2Z" fill="currentColor" />
    </svg>
  ),
  Actions: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M14 3a1 1 0 0 1 1 1v4h4a1 1 0 1 1 0 2h-4v4a1 1 0 1 1-2 0v-4H9a1 1 0 1 1 0-2h4V4a1 1 0 0 1 1-1ZM5 13h5a1 1 0 1 1 0 2H6v3h3a1 1 0 1 1 0 2H5a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1Zm10 4h4a1 1 0 1 1 0 2h-5a1 1 0 0 1-1-1v-5a1 1 0 1 1 2 0v4Z" fill="currentColor" />
    </svg>
  ),
  Settings: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 8.5A3.5 3.5 0 1 0 12 15.5 3.5 3.5 0 1 0 12 8.5Zm8.3 2.6-1.7-.3a6.8 6.8 0 0 0-.6-1.5l1-1.4-1.4-1.4-1.4 1a6.8 6.8 0 0 0-1.5-.6l-.3-1.7h-2l-.3 1.7a6.8 6.8 0 0 0-1.5.6l-1.4-1-1.4 1.4 1 1.4a6.8 6.8 0 0 0-.6 1.5l-1.7.3v2l1.7.3a6.8 6.8 0 0 0 .6 1.5l-1 1.4 1.4 1.4 1.4-1a6.8 6.8 0 0 0 1.5.6l.3 1.7h2l.3-1.7a6.8 6.8 0 0 0 1.5-.6l1.4 1 1.4-1.4-1-1.4a6.8 6.8 0 0 0 .6-1.5l1.7-.3v-2Z" fill="currentColor" />
    </svg>
  ),
  Logs: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M13 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-7-5ZM6 19V5h6v4h5v10H6Zm2-3h8v2H8v-2Zm0-4h8v2H8v-2Zm0-4h3v2H8V8Z" fill="currentColor" />
    </svg>
  ),
  Bluetooth: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M17.71 7.71L12 2h-1v7.59L6.41 5L5 6.41 10.59 12L5 17.59 6.41 19L11 14.41V22h1l5.71-5.71L12.41 12l5.3-4.29zM13 5.83l1.88 1.88L13 9.59V5.83zm1.88 10.29L13 18.17v-3.76l1.88 1.88z" fill="currentColor" />
    </svg>
  ),
};

export default function Sidebar({ tabs, activeTab, onSelect, drawerOpen }: Props) {
  return (
    <aside className={`sidebar ${drawerOpen ? "open" : ""}`} style={{ width: "var(--nav-width)" }}>
      <div className="sidebar-title">BinaryStars</div>
      {tabs.map((tab) => (
        <button
          className={`nav-btn ${tab === activeTab ? "active" : ""}`}
          key={tab}
          onClick={() => onSelect(tab)}
          type="button"
        >
          <span className="nav-icon" aria-hidden="true">
            {iconMap[tab]}
          </span>
          <span className="nav-label">{tab}</span>
        </button>
      ))}
    </aside>
  );
}
