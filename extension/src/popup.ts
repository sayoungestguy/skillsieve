import { saveJob } from "./api.js";
import { extractJob, type ExtractedJob } from "./extract.js";
import { loadSettings } from "./settings.js";

const $ = <T extends HTMLElement>(id: string) =>
  document.getElementById(id) as T;

function show(
  state: "setup" | "ready" | "busy" | "done",
  message = "",
  link?: string,
): void {
  $("setup").hidden = state !== "setup";
  $("ready").hidden = state === "setup";
  $<HTMLButtonElement>("save").disabled = state === "busy" || state === "done";
  $("message").textContent = message;
  const a = $<HTMLAnchorElement>("open");
  a.hidden = !link;
  if (link) a.href = link;
}

async function readTab(): Promise<ExtractedJob | null> {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (tab?.id === undefined) return null;
  const [res] = await chrome.scripting.executeScript({
    target: { tabId: tab.id },
    func: extractJob,
  });
  return (res?.result as ExtractedJob | undefined) ?? null;
}

async function main(): Promise<void> {
  $("options").addEventListener("click", () =>
    chrome.runtime.openOptionsPage(),
  );
  const settings = await loadSettings();
  if (!settings) {
    show("setup");
    return;
  }

  let job: ExtractedJob | null = null;
  try {
    job = await readTab();
  } catch {
    show(
      "ready",
      "Can't read this page (Chrome pages and the Web Store are off limits).",
    );
    $<HTMLButtonElement>("save").disabled = true;
    return;
  }
  if (!job) return;
  $("title").textContent = job.title ?? "Untitled job";
  $("company").textContent =
    job.company ?? (job.site === "selection" ? "From your selection" : "");
  $("chars").textContent = `${job.text.length.toLocaleString()} characters`;
  show("ready");

  $("save").addEventListener("click", async () => {
    if (!job) return;
    show("busy", "Saving…");
    const r = await saveJob(settings, job);
    const jobLink = (id: string) => `${settings.apiBase}/jobs/${id}`;
    switch (r.kind) {
      case "saved":
        show("done", "Saved. Skills are being extracted.", jobLink(r.jobId));
        break;
      case "duplicate":
        show(
          "done",
          "You already saved this post.",
          r.jobId ? jobLink(r.jobId) : undefined,
        );
        break;
      case "too_short":
        show(
          "ready",
          "Not enough text found. Select the job description on the page and try again.",
        );
        break;
      case "bad_token":
        show(
          "ready",
          "Your token is not valid any more. Create a new one in SkillSieve → Settings.",
        );
        break;
      case "error":
        show("ready", r.message);
        break;
    }
  });
}

void main();
