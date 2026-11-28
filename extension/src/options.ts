import { whoAmI } from "./api.js";
import {
  loadSettings,
  normaliseBase,
  originPattern,
  saveSettings,
} from "./settings.js";

const $ = <T extends HTMLElement>(id: string) =>
  document.getElementById(id) as T;

async function main(): Promise<void> {
  const current = await loadSettings();
  if (current) {
    $<HTMLInputElement>("apiBase").value = current.apiBase;
    $<HTMLInputElement>("token").value = current.token;
  }
  $<HTMLFormElement>("form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const status = $("status");
    status.textContent = "";
    let apiBase: string;
    try {
      apiBase = normaliseBase($<HTMLInputElement>("apiBase").value);
    } catch (err) {
      status.textContent =
        err instanceof Error ? err.message : "Invalid address.";
      return;
    }
    const token = $<HTMLInputElement>("token").value.trim();
    if (!token.startsWith("ssv_")) {
      status.textContent =
        "Tokens start with ssv_. Create one in SkillSieve → Settings.";
      return;
    }
    // Ask for access to this one site only (needed to call its API from the extension).
    const granted = await chrome.permissions.request({
      origins: [originPattern(apiBase)],
    });
    if (!granted) {
      status.textContent =
        "The extension needs permission to talk to your SkillSieve site.";
      return;
    }
    const settings = { apiBase, token };
    const email = await whoAmI(settings).catch(() => null);
    if (!email) {
      status.textContent = `Could not sign in to ${apiBase} with this token.`;
      return;
    }
    await saveSettings(settings);
    status.textContent = `Connected as ${email}.`;
  });
}

void main();
