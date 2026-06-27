import React, { useState, useEffect } from "react";
import { invoke } from "@tauri-apps/api/core";
import "../../features/features.css";

interface SystemMonitor {
  name: string;
  width: number;
  height: number;
  x: number;
  y: number;
  scaleFactor: number;
}

interface TabletPanelProps {
  isDark: boolean;
  connected: boolean;
  peerId: string;
}

export const TabletPanel: React.FC<TabletPanelProps> = ({
  isDark,
  connected,
  peerId,
}) => {
  const [screens, setScreens] = useState<SystemMonitor[]>([]);
  const [selectedName, setSelectedName] = useState<string>("");
  const [loading, setLoading] = useState<boolean>(true);

  const isDirectConnection = connected && peerId && peerId !== "Group Chat Session";

  const fetchScreens = async () => {
    try {
      setLoading(true);
      const list = await invoke<SystemMonitor[]>("get_available_screens");
      setScreens(list);

      if (list.length > 0) {
        const stored = localStorage.getItem("binarystars_last_screen");
        const found = list.find((s) => s.name === stored);
        const active = found || list[0];
        
        setSelectedName(active.name);
        await invoke("set_mapped_screen", {
          name: active.name,
          x: active.x,
          y: active.y,
          width: active.width,
          height: active.height,
        });
      }
    } catch (err) {
      console.error("Failed to fetch monitors:", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchScreens();
  }, []);

  const handleSelectScreen = async (screen: SystemMonitor) => {
    setSelectedName(screen.name);
    localStorage.setItem("binarystars_last_screen", screen.name);
    try {
      await invoke("set_mapped_screen", {
        name: screen.name,
        x: screen.x,
        y: screen.y,
        width: screen.width,
        height: screen.height,
      });
    } catch (err) {
      console.error("Failed to map screen:", err);
    }
  };

  const selectedScreen = screens.find((s) => s.name === selectedName);

  // Compute visualization aspect ratio style
  let ratioStyle: React.CSSProperties = { aspectRatio: "16/9" };
  if (selectedScreen) {
    const r = selectedScreen.width / selectedScreen.height;
    ratioStyle = { aspectRatio: `${r}` };
  }

  return (
    <div className="dashboard-container">
      <div className={`glass-card ${isDark ? "dark" : "light"}`} style={{ padding: "20px" }}>
        <h2 style={{ fontSize: "18px", fontWeight: 600, marginBottom: "8px", display: "flex", alignItems: "center", gap: "8px" }}>
          <span>🎨</span> Drawing Tablet Mapping
        </h2>
        <p style={{ fontSize: "13px", opacity: 0.8, lineHeight: "1.5", marginBottom: "20px" }}>
          Map your Android device's drawing canvas directly onto one of your Linux displays. Touch coordinates from your phone are scaled absolutely to the selected monitor's resolution.
        </p>

        {!isDirectConnection && (
          <div className="tablet-warning-banner" style={{
            background: isDark ? "rgba(235, 94, 40, 0.1)" : "rgba(235, 94, 40, 0.05)",
            border: "1px solid rgba(235, 94, 40, 0.3)",
            borderRadius: "6px",
            padding: "16px",
            color: isDark ? "#ff9e7d" : "#c25e34",
            fontSize: "13px",
            lineHeight: "1.5"
          }}>
            <strong>🛸 1-on-1 Connection Required</strong>
            <p style={{ margin: "6px 0 0 0", opacity: 0.9 }}>
              This feature requires an active, direct bluetooth connection between your phone and this Linux device. Please go to the <strong>Discovery</strong> panel and connect first.
            </p>
          </div>
        )}

        {isDirectConnection && (
          <div style={{ display: "flex", flexDirection: "column", gap: "24px" }}>
            {/* Screen selection */}
            <div>
              <h3 style={{ fontSize: "14px", fontWeight: 600, marginBottom: "12px", color: isDark ? "var(--text-primary-dark)" : "var(--text-primary-light)" }}>
                Select Display Target
              </h3>
              {loading ? (
                <div style={{ fontSize: "12px", opacity: 0.6 }}>Detecting screens...</div>
              ) : screens.length === 0 ? (
                <div style={{ fontSize: "12px", color: "red" }}>No screens detected!</div>
              ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
                  {screens.map((scr) => (
                    <div
                      key={scr.name}
                      onClick={() => handleSelectScreen(scr)}
                      style={{
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "center",
                        padding: "12px 16px",
                        borderRadius: "6px",
                        border: "1px solid",
                        borderColor: scr.name === selectedName
                          ? "var(--accent-color)"
                          : (isDark ? "var(--border-dark)" : "var(--border-light)"),
                        background: scr.name === selectedName
                          ? (isDark ? "rgba(35, 131, 226, 0.1)" : "rgba(35, 131, 226, 0.05)")
                          : "transparent",
                        cursor: "pointer",
                        transition: "all 0.2s ease"
                      }}
                      className="screen-select-item"
                    >
                      <div>
                        <h4 style={{ fontSize: "13px", fontWeight: 600, margin: 0 }}>
                          {scr.name} {scr.name === selectedName && "⭐"}
                        </h4>
                        <span style={{ fontSize: "11px", opacity: 0.6, marginTop: "4px", display: "inline-block" }}>
                          Resolution: {scr.width} x {scr.height} | Offset: x={scr.x}, y={scr.y}
                        </span>
                      </div>
                      <span style={{ fontSize: "11px", fontWeight: 600, color: scr.name === selectedName ? "var(--accent-color)" : "inherit" }}>
                        {scr.name === selectedName ? "Mapped" : "Select"}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Visual preview */}
            {selectedScreen && (
              <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
                <h3 style={{ fontSize: "14px", fontWeight: 600, color: isDark ? "var(--text-primary-dark)" : "var(--text-primary-light)" }}>
                  Mapping Ratio Visualization
                </h3>
                <div style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  background: isDark ? "rgba(0, 0, 0, 0.2)" : "rgba(0, 0, 0, 0.03)",
                  border: isDark ? "1px solid var(--border-dark)" : "1px solid var(--border-light)",
                  borderRadius: "6px",
                  padding: "40px",
                  minHeight: "200px"
                }}>
                  <div
                    style={{
                      width: "100%",
                      maxWidth: "280px",
                      background: isDark ? "rgba(35, 131, 226, 0.05)" : "rgba(35, 131, 226, 0.02)",
                      border: "2px dashed var(--accent-color)",
                      boxShadow: isDark ? "0 0 15px rgba(35, 131, 226, 0.15)" : "none",
                      borderRadius: "8px",
                      display: "flex",
                      flexDirection: "column",
                      alignItems: "center",
                      justifyContent: "center",
                      color: "var(--accent-color)",
                      fontWeight: 600,
                      fontSize: "12px",
                      padding: "16px",
                      boxSizing: "border-box",
                      ...ratioStyle
                    }}
                  >
                    <span>{selectedScreen.name}</span>
                    <span style={{ fontSize: "10px", opacity: 0.8, marginTop: "4px" }}>
                      {selectedScreen.width}x{selectedScreen.height} ({Math.round(selectedScreen.width / selectedScreen.height * 100) / 100}:1)
                    </span>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
