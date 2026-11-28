import type { Settings } from "./settings";

export type SaveResult =
  | { kind: "saved"; jobId: string }
  | { kind: "duplicate"; jobId: string | null }
  | { kind: "too_short" }
  | { kind: "bad_token" }
  | { kind: "error"; message: string };

const HEADERS = {
  "X-Requested-With": "SkillSieveExtension",
  "Content-Type": "application/json",
};

function auth(s: Settings): Record<string, string> {
  return { ...HEADERS, Authorization: `Bearer ${s.token}` };
}

/** POST /api/jobs with the extension token. Never sends cookies. */
export async function saveJob(
  s: Settings,
  job: { text: string; url: string },
  fetchFn: typeof fetch = fetch,
): Promise<SaveResult> {
  if (job.text.length < 200) return { kind: "too_short" };
  const sourceUrl =
    /^https?:\/\/\S+$/.test(job.url) && job.url.length <= 2048
      ? job.url
      : undefined;
  let res: Response;
  try {
    res = await fetchFn(`${s.apiBase}/api/jobs`, {
      method: "POST",
      credentials: "omit",
      headers: auth(s),
      body: JSON.stringify({
        rawText: job.text,
        ...(sourceUrl && { sourceUrl }),
      }),
    });
  } catch {
    return { kind: "error", message: `Cannot reach ${s.apiBase}.` };
  }
  if (res.status === 201) {
    const body = (await res.json()) as { id: string };
    return { kind: "saved", jobId: body.id };
  }
  if (res.status === 401) return { kind: "bad_token" };
  const problem = (await res.json().catch(() => ({}))) as {
    detail?: string;
    existingId?: string;
  };
  if (res.status === 409)
    return { kind: "duplicate", jobId: problem.existingId ?? null };
  return {
    kind: "error",
    message: problem.detail ?? `SkillSieve answered ${res.status}.`,
  };
}

/** GET /api/me: who the token belongs to, or null if the token is not valid. */
export async function whoAmI(
  s: Settings,
  fetchFn: typeof fetch = fetch,
): Promise<string | null> {
  const res = await fetchFn(`${s.apiBase}/api/me`, {
    credentials: "omit",
    headers: auth(s),
  });
  if (!res.ok) return null;
  return ((await res.json()) as { email: string }).email;
}
