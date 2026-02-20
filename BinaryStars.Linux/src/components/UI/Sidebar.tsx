type Props = {
  tabs: string[];
  activeTab: string;
  onSelect: (tab: string) => void;
  drawerOpen: boolean;
};

export default function Sidebar({ tabs, activeTab, onSelect, drawerOpen }: Props) {
  return (
    <aside className={`sidebar ${drawerOpen ? "open" : ""}`}>
      <div className="sidebar-title">BinaryStars</div>
      {tabs.map((tab) => (
        <button
          className={`nav-btn ${tab === activeTab ? "active" : ""}`}
          key={tab}
          onClick={() => onSelect(tab)}
          type="button"
        >
          {tab}
        </button>
      ))}
    </aside>
  );
}
