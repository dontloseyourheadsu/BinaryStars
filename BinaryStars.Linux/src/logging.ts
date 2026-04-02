import { invoke } from "@tauri-apps/api/core";
import { isTauriRuntime } from "./tauriDeviceInfo";

type LogLevel = "trace" | "debug" | "info" | "warn" | "error";

type LogMeta = Record<string, unknown> | undefined;

let interactionLoggingInstalled = false;

function stringifyMeta(meta: LogMeta): string | null {
  if (!meta) {
    return null;
  }

  try {
    return JSON.stringify(meta);
  } catch {
    return null;
  }
}

function writeConsole(level: LogLevel, category: string, message: string, meta?: LogMeta): void {
  const prefix = `[${level.toUpperCase()}] [${category}] ${message}`;
  if (level === "error") {
    console.error(prefix, meta ?? "");
    return;
  }

  if (level === "warn") {
    console.warn(prefix, meta ?? "");
    return;
  }

  if (level === "debug" || level === "trace") {
    console.debug(prefix, meta ?? "");
    return;
  }

  console.log(prefix, meta ?? "");
}

async function writeNative(level: LogLevel, category: string, message: string, meta?: LogMeta): Promise<void> {
  if (!isTauriRuntime()) {
    return;
  }

  try {
    await invoke("append_ui_log", {
      level,
      category,
      message,
      metadataJson: stringifyMeta(meta),
    });
  } catch {
    // Keep app flow resilient if logging fails.
  }
}

export function logEvent(level: LogLevel, category: string, message: string, meta?: LogMeta): void {
  writeConsole(level, category, message, meta);
  void writeNative(level, category, message, meta);
}

export function installInteractionLogging(): void {
  if (interactionLoggingInstalled || typeof document === "undefined") {
    return;
  }

  interactionLoggingInstalled = true;

  const describeTarget = (target: EventTarget | null): string => {
    const element = target instanceof HTMLElement ? target : null;
    if (!element) {
      return "unknown";
    }

    const idPart = element.id ? `#${element.id}` : "";
    const namePart = element.getAttribute("name") ? `[name=${element.getAttribute("name")}]` : "";
    const ariaPart = element.getAttribute("aria-label") ? `[aria-label=${element.getAttribute("aria-label")}]` : "";
    return `${element.tagName.toLowerCase()}${idPart}${namePart}${ariaPart}`;
  };

  document.addEventListener("click", (event) => {
    logEvent("debug", "ui.interaction", "click", {
      target: describeTarget(event.target),
    });
  }, true);

  document.addEventListener("change", (event) => {
    const element = event.target instanceof HTMLInputElement
      || event.target instanceof HTMLTextAreaElement
      || event.target instanceof HTMLSelectElement
      ? event.target
      : null;

    if (!element) {
      return;
    }

    logEvent("debug", "ui.interaction", "input-change", {
      target: describeTarget(event.target),
      valueLength: typeof element.value === "string" ? element.value.length : 0,
    });
  }, true);
}

export async function getNativeLogFilePath(): Promise<string | null> {
  if (!isTauriRuntime()) {
    return null;
  }

  try {
    return await invoke<string>("get_log_file_path");
  } catch {
    return null;
  }
}
