import React from "react";
import "../../features/features.css";

interface KeyboardPanelProps {
  isDark: boolean;
  connected: boolean;
  peerId: string;
}

export const KeyboardPanel: React.FC<KeyboardPanelProps> = ({
  isDark,
  connected,
  peerId,
}) => {
  const isDirectConnection = connected && peerId && peerId !== "Group Chat Session";

  return (
    <div className="dashboard-container">
      <div className={`glass-card ${isDark ? "dark" : "light"}`} style={{ padding: "20.5px" }}>
        <h2 style={{ fontSize: "18px", fontWeight: 600, marginBottom: "8px", display: "flex", alignItems: "center", gap: "8px" }}>
          <span>⌨️</span> Keyboard Controller Mode
        </h2>
        <p style={{ fontSize: "13px", opacity: 0.8, lineHeight: "1.5", marginBottom: "20px" }}>
          Use your connected phone as an external keyboard and remote controller. Keystrokes, navigation commands, and key combinations are simulated directly into your active window.
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
          <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
            {/* Status indicators */}
            <div style={{
              display: "flex",
              alignItems: "center",
              gap: "12px",
              padding: "16px",
              borderRadius: "8px",
              border: `1px solid ${isDark ? "var(--border-dark)" : "var(--border-light)"}`,
              background: isDark ? "rgba(255, 255, 255, 0.02)" : "rgba(0, 0, 0, 0.01)",
            }}>
              <span className="pulsing-dot" style={{
                width: "10px",
                height: "10px",
                borderRadius: "50%",
                background: "#10b981",
                display: "inline-block",
                boxShadow: "0 0 8px #10b981"
              }}></span>
              <div>
                <h4 style={{ fontSize: "14px", fontWeight: 600, margin: 0 }}>
                  Keyboard Listener Active
                </h4>
                <p style={{ fontSize: "11px", opacity: 0.6, margin: "2px 0 0 0" }}>
                  Connected to client: <span style={{ fontFamily: "monospace", fontWeight: 600 }}>{peerId}</span>
                </p>
              </div>
            </div>

            {/* Instruction Steps */}
            <div>
              <h3 style={{ fontSize: "14px", fontWeight: 600, marginBottom: "12px", color: isDark ? "var(--text-primary-dark)" : "var(--text-primary-light)" }}>
                How to Use
              </h3>
              <ol style={{
                fontSize: "13px",
                opacity: 0.8,
                lineHeight: "1.8",
                paddingLeft: "20px",
                margin: 0,
                display: "flex",
                flexDirection: "column",
                gap: "8px"
              }}>
                <li>
                  Click onto any input field, terminal, or text document on this Linux machine to give it cursor focus.
                </li>
                <li>
                  Open the <strong>Keyboard Mode</strong> on your Android app.
                </li>
                <li>
                  Tap the input pad at the bottom of your phone screen to toggle the keyboard, or use the D-pad and modifier buttons.
                </li>
                <li>
                  Keystrokes will type directly into the focused window. Modifiers (Ctrl, Alt) will remain active on subsequent inputs when toggled on the phone.
                </li>
              </ol>
            </div>

            {/* Simulated Keys Reference */}
            <div style={{
              marginTop: "8px",
              padding: "16px",
              borderRadius: "8px",
              background: isDark ? "rgba(35, 131, 226, 0.05)" : "rgba(35, 131, 226, 0.02)",
              border: "1px solid rgba(35, 131, 226, 0.15)"
            }}>
              <h4 style={{ fontSize: "13px", fontWeight: 600, margin: "0 0 8px 0", color: "var(--accent-color)" }}>
                ⚡ Supported Keystrokes & Modifiers
              </h4>
              <div style={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fill, minmax(100px, 1fr))",
                gap: "8px",
                fontSize: "11px",
                opacity: 0.8,
                textAlign: "center"
              }}>
                <span style={{ padding: "4px", background: isDark ? "#222" : "#eee", borderRadius: "4px" }}>Ctrl / Alt / Shift</span>
                <span style={{ padding: "4px", background: isDark ? "#222" : "#eee", borderRadius: "4px" }}>Super / Meta</span>
                <span style={{ padding: "4px", background: isDark ? "#222" : "#eee", borderRadius: "4px" }}>Esc / Tab / Space</span>
                <span style={{ padding: "4px", background: isDark ? "#222" : "#eee", borderRadius: "4px" }}>Backspace / Enter</span>
                <span style={{ padding: "4px", background: isDark ? "#222" : "#eee", borderRadius: "4px" }}>Home / End</span>
                <span style={{ padding: "4px", background: isDark ? "#222" : "#eee", borderRadius: "4px" }}>Page Up / Down</span>
                <span style={{ padding: "4px", background: isDark ? "#222" : "#eee", borderRadius: "4px" }}>Insert / Delete</span>
                <span style={{ padding: "4px", background: isDark ? "#222" : "#eee", borderRadius: "4px" }}>Arrow Keys</span>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
