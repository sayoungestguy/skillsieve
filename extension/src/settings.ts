/** Where to send jobs and with which token. Stored in chrome.storage.local (this browser only). */
export interface Settings {
  apiBase: string;
  token: string;
}

export async function loadSettings(): Promise<Settings | null> {
  const s = await chrome.storage.local.get(["apiBase", "token"]);
  if (
    typeof s.apiBase !== "string" ||
    typeof s.token !== "string" ||
    !s.apiBase ||
    !s.token
  ) {
    return null;
  }
  return { apiBase: s.apiBase, token: s.token };
}

export async function saveSettings(s: Settings): Promise<void> {
  await chrome.storage.local.set(s);
}

/** "https://jobs.example.com/" → "https://jobs.example.com". Throws on anything but http(s). */
export function normaliseBase(input: string): string {
  const url = new URL(input.trim());
  if (url.protocol !== "https:" && url.protocol !== "http:")
    throw new Error("Use an http(s) address.");
  if (
    url.protocol === "http:" &&
    !["localhost", "127.0.0.1"].includes(url.hostname)
  ) {
    throw new Error("Use https (plain http only for localhost).");
  }
  return url.origin;
}

/** Host permission pattern for chrome.permissions.request. */
export function originPattern(apiBase: string): string {
  const url = new URL(apiBase);
  return `${url.protocol}//${url.hostname}/*`;
}
