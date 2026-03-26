import type { ChangeEvent, RefObject } from "react";
import { useMemo, useState } from "react";
import type { FileTransfer } from "../../types";
import { formatSize, statusLabel } from "../../utils/helpers";

type Props = {
  devices: Array<{ id: string; name: string; isOnline: boolean }>;
  myDeviceId: string;
  selectedTargetDeviceId: string;
  onSelectTargetDevice: (deviceId: string) => void;
  onClearSent: () => void;
  onClearReceived: () => void;
  transfers: FileTransfer[];
  filePickerRef: RefObject<HTMLInputElement | null>;
  onPickFile: (event: ChangeEvent<HTMLInputElement>) => void;
  onDownloadTransfer: (transfer: FileTransfer) => void;
  onRejectTransfer: (transfer: FileTransfer) => void;
};

export default function FilesTab({
  devices,
  myDeviceId,
  selectedTargetDeviceId,
  onSelectTargetDevice,
  onClearSent,
  onClearReceived,
  transfers,
  filePickerRef,
  onPickFile,
  onDownloadTransfer,
  onRejectTransfer,
}: Props) {
  const [scope, setScope] = useState<"all" | "sent" | "received">("all");
  const targets = devices.filter((entry) => entry.id !== myDeviceId && entry.isOnline);
  const deviceLookup = useMemo(() => {
    return new Map(devices.map((entry) => [entry.id, entry.name]));
  }, [devices]);

  const visibleTransfers = useMemo(() => {
    if (scope === "sent") {
      return transfers.filter((entry) => entry.isSender);
    }
    if (scope === "received") {
      return transfers.filter((entry) => !entry.isSender);
    }

    return transfers;
  }, [scope, transfers]);

  return (
    <section className="panel-stack">
      <div className="panel">
        <div className="split-row">
          <h3>File Transfers</h3>
          <select
            aria-label="Target device"
            onChange={(event) => onSelectTargetDevice(event.target.value)}
            title="Target device"
            value={selectedTargetDeviceId}
          >
            <option value="">Choose target</option>
            {targets.map((device) => (
              <option key={device.id} value={device.id}>
                {device.name} ({device.id})
              </option>
            ))}
          </select>
          <button onClick={() => filePickerRef.current?.click()} type="button">
            Send
          </button>
        </div>
        <input hidden onChange={onPickFile} ref={filePickerRef} type="file" />
        <div className="chip-row">
          <button className={scope === "all" ? "" : "ghost"} onClick={() => setScope("all")} type="button">All</button>
          <button className={scope === "sent" ? "" : "ghost"} onClick={() => setScope("sent")} type="button">Sent</button>
          <button className={scope === "received" ? "" : "ghost"} onClick={() => setScope("received")} type="button">Received</button>
          <button className="ghost" onClick={onClearSent} type="button">Clear Sent</button>
          <button className="ghost" onClick={onClearReceived} type="button">Clear Received</button>
        </div>
        {visibleTransfers.length === 0 && <p className="empty-state">No transfers yet.</p>}
        <div className="list">
          {visibleTransfers.map((transfer) => (
            <article className="row-card static" key={transfer.id}>
              <strong>{transfer.fileName}</strong>
              <span className="muted">
                {transfer.isSender
                  ? `To ${deviceLookup.get(transfer.targetDeviceId) ?? transfer.targetDeviceId}`
                  : `From ${deviceLookup.get(transfer.senderDeviceId) ?? transfer.senderDeviceId}`} · {" "}
                {formatSize(transfer.sizeBytes)} · {statusLabel(transfer.status, transfer.isSender)}
              </span>
              <div className="chip-row">
                {!transfer.isSender && transfer.status === "Available" && (
                  <button onClick={() => onDownloadTransfer(transfer)} type="button">
                    Download
                  </button>
                )}
                {!transfer.isSender && transfer.status === "Available" && (
                  <button className="ghost" onClick={() => onRejectTransfer(transfer)} type="button">
                    Reject
                  </button>
                )}
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
