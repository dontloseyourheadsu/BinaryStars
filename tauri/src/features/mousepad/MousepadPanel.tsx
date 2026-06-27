import React from "react";
import "../../features/features.css";

interface MousepadPanelProps {
  isDark: boolean;
  connected: boolean;
  peerId: string;
}

export const MousepadPanel: React.FC<MousepadPanelProps> = ({
  isDark,
  connected,
  peerId,
}) => {
  const isDirectConnection = connected && peerId && peerId !== "Group Chat Session";

  return (
    <div className="dashboard-container">
      <div className={`glass-card ${isDark ? "dark" : "light"}`} style={{ padding: "20.5px" }}>
        <h2 style={{ fontSize: "18px", fontWeight: 600, marginBottom: "8px", display: "flex", alignItems: "center", gap: "8px" }}>
          <span>🖱️</span> Trackpad Mode
        </h2>
        <p style={{ fontSize: "13px", opacity: 0.8, lineHeight: "1.5", marginBottom: "20px" }}>
          Transform your phone into a remote touchpad. Move the pointer relatively, tap to click, and perform drag-and-drop operations directly from your phone.
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
            {/* Status Indicator */}
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
                background: "#2383e2",
                display: "inline-block",
                boxShadow: "0 0 8px #2383e2"
              }}></span>
              <div>
                <h4 style={{ fontSize: "14px", fontWeight: 600, margin: 0 }}>
                  Trackpad Listener Active
                </h4>
                <p style={{ fontSize: "11px", opacity: 0.6, margin: "2px 0 0 0" }}>
                  Receiving pointer coordinates from: <span style={{ fontFamily: "monospace", fontWeight: 600 }}>{peerId}</span>
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
                  Open <strong>Trackpad Mode</strong> on your Android app (the hand icon 👆).
                </li>
                <li>
                  Slide your finger inside the large trackpad zone on your phone to move this system's mouse cursor relatively.
                </li>
                <li>
                  Adjust the sensitivity slider on your phone to change the cursor speed.
                </li>
                <li>
                  Tap or hold the <strong>LEFT CLICK</strong> and <strong>RIGHT CLICK</strong> buttons.
                </li>
                <li>
                  To drag-and-select or drag window panels: hold the <strong>LEFT CLICK</strong> button down on your phone with one finger, and slide another finger on the trackpad zone.
                </li>
              </ol>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
