/** Thin fetch wrapper. All HTTP calls in the app go through here (CODING_STANDARDS §5). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly problem: { title?: string; detail?: string; existingId?: string } = {},
  ) {
    super(problem.detail ?? problem.title ?? `Request failed (${status})`);
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(path, {
    ...init,
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'X-Requested-With': 'XMLHttpRequest',
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...init.headers,
    },
  });
  if (!res.ok) {
    const problem = (await res.json().catch(() => ({}))) as ConstructorParameters<
      typeof ApiError
    >[1];
    throw new ApiError(res.status, problem);
  }
  return (res.status === 204 ? undefined : await res.json()) as T;
}

export function toQuery(params: Record<string, string | number | undefined>): string {
  const q = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== '') q.set(k, String(v));
  const s = q.toString();
  return s ? `?${s}` : '';
}
