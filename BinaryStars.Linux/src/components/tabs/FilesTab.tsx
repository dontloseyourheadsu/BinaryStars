import type { ChangeEvent, RefObject } from "react";
import type { FileTransfer } from "../../types";
import { formatSize, statusLabel } from "../../utils/helpers";

type Props = {
  transfers: FileTransfer[];
  filePickerRef: RefObject<HTMLInputElement | null>;
  onPickFile: (event: ChangeEvent<HTMLInputElement>) => void;
  onDownloadTransfer: (transfer: FileTransfer) => void;
  onRejectTransfer: (transfer: FileTransfer) => void;
};

export default function FilesTab({
  transfers,
  filePickerRef,
  onPickFile,
  onDownloadTransfer,
  onRejectTransfer,
}: Props) {
  return (
    <section className="panel-stack">
      <div className="panel">
        <div className="split-row">
          <h3>File Transfers</h3>
          <button onClick={() => filePickerRef.current?.click()} type="button">
            Send
          </button>
        </div>
        <input hidden onChange={onPickFile} ref={filePickerRef} type="file" />
        {transfers.length === 0 && <p className="empty-state">No transfers yet.</p>}
        <div className="list">
          {transfers.map((transfer) => (
            <article className="row-card static" key={transfer.id}>
              <strong>{transfer.fileName}</strong>
              <span className="muted">
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
