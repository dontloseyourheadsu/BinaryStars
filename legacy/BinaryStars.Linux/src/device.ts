function randomHex(size: number): string {
  const chars = "abcdef0123456789";
  let value = "";
  for (let index = 0; index < size; index += 1) {
    value += chars[Math.floor(Math.random() * chars.length)];
  }
  return value;
}

const DEVICE_ID_KEY = "binarystars.device.id";
const DEVICE_NAME_KEY = "binarystars.device.name";

export function getDeviceId(): string {
  const existing = localStorage.getItem(DEVICE_ID_KEY);
  if (existing) {
    return existing;
  }
  const generated = `linux-${randomHex(12)}`;
  localStorage.setItem(DEVICE_ID_KEY, generated);
  return generated;
}

export function getDeviceName(): string {
  const existing = localStorage.getItem(DEVICE_NAME_KEY);
  if (existing) {
    return existing;
  }
  const fallback = navigator.userAgent.includes("Linux") ? "Linux Desktop" : "Desktop Client";
  localStorage.setItem(DEVICE_NAME_KEY, fallback);
  return fallback;
}

export function setDeviceName(name: string): void {
  localStorage.setItem(DEVICE_NAME_KEY, name);
}