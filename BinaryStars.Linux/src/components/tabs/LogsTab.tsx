import { useEffect } from "react";

type Props = {
  logContent: string;
  onRefreshLogs: () => void;
};

export default function LogsTab({ logContent, onRefreshLogs }: Props) {
  useEffect(() => {
    onRefreshLogs();
    const timer = window.setInterval(onRefreshLogs, 5000);
    return () => window.clearInterval(timer);
  }, [onRefreshLogs]);

  return (
    <section className="panel-stack">
      <div className="panel" style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        <div className="actions-row">
          <h3>System Logs</h3>
          <button type="button" onClick={onRefreshLogs}>Refresh</button>
        </div>
        
        <div style={{ 
          marginTop: '16px', 
          background: '#121212', 
          color: '#00FF00', 
          padding: '16px', 
          borderRadius: '8px', 
          flex: 1,
          overflowY: 'auto', 
          fontFamily: 'monospace', 
          fontSize: '12px' 
        }}>
          <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>
            {logContent || "No logs captured yet."}
          </pre>
        </div>
      </div>
    </section>
  );
}
