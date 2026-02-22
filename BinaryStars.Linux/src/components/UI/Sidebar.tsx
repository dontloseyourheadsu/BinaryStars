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
  Map: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M15 4l5-2v16l-5 2-6-2-5 2V4l5-2 6 2Zm-1 2.2-4-1.33V17.8l4 1.33V6.2Zm-9-.87v12.34l3-1.2V4.13l-3 1.2Zm14 11.34V4.33l-3 1.2v12.34l3-1.2Z" fill="currentColor" />
    </svg>
  ),
  Settings: (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 8.5A3.5 3.5 0 1 0 12 15.5 3.5 3.5 0 1 0 12 8.5Zm8.3 2.6-1.7-.3a6.8 6.8 0 0 0-.6-1.5l1-1.4-1.4-1.4-1.4 1a6.8 6.8 0 0 0-1.5-.6l-.3-1.7h-2l-.3 1.7a6.8 6.8 0 0 0-1.5.6l-1.4-1-1.4 1.4 1 1.4a6.8 6.8 0 0 0-.6 1.5l-1.7.3v2l1.7.3a6.8 6.8 0 0 0 .6 1.5l-1 1.4 1.4 1.4 1.4-1a6.8 6.8 0 0 0 1.5.6l.3 1.7h2l.3-1.7a6.8 6.8 0 0 0 1.5-.6l1.4 1 1.4-1.4-1-1.4a6.8 6.8 0 0 0 .6-1.5l1.7-.3v-2Z" fill="currentColor" />
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
