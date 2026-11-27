import { useState, type FormEvent } from 'react';
import { ApiError } from '@/api/client';
import { useCreateToken, useRevokeToken, useTokens } from '../hooks';

const dateFmt = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' });

/** Create and revoke tokens for the SkillSieve Chrome extension (one per browser). */
export function ExtensionTokens() {
  const { data: tokens = [], isPending } = useTokens();
  const create = useCreateToken();
  const revoke = useRevokeToken();
  const [name, setName] = useState('Chrome');
  const [fresh, setFresh] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const onCreate = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setCopied(false);
    try {
      const created = await create.mutateAsync(name.trim() || 'Chrome');
      setFresh(created.token);
    } catch (err) {
      setError(err instanceof ApiError ? (err.problem.detail ?? err.message) : 'Could not create.');
    }
  };

  const copy = async () => {
    if (!fresh) return;
    await navigator.clipboard.writeText(fresh);
    setCopied(true);
  };

  return (
    <section className="panel flex flex-col gap-4" aria-labelledby="ext">
      <div>
        <h2 id="ext" className="text-lg">
          Browser extension
        </h2>
        <p className="mt-1 text-sm text-muted">
          Save job posts from LinkedIn and MyCareersFuture in one click. Create a token, then paste
          it into the extension&apos;s options with this site&apos;s address (
          {window.location.origin}). A token can only save jobs.
        </p>
      </div>

      {fresh && (
        <div role="status" className="flex flex-col gap-2 rounded-lg border border-line bg-bg p-3">
          <p className="m-0 text-sm font-semibold">
            Copy your token now. It won&apos;t be shown again.
          </p>
          <div className="flex flex-wrap items-center gap-2">
            <code className="min-w-0 flex-1 break-all text-sm" aria-label="New token">
              {fresh}
            </code>
            <button type="button" className="btn btn-secondary" onClick={copy}>
              {copied ? 'Copied' : 'Copy'}
            </button>
          </div>
        </div>
      )}

      <form className="flex flex-wrap items-end gap-3" onSubmit={onCreate}>
        <div className="flex-[1_1_240px]">
          <label className="label" htmlFor="tok-name">
            Token name
          </label>
          <input
            id="tok-name"
            className="field"
            maxLength={60}
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </div>
        <button type="submit" className="btn btn-primary" disabled={create.isPending}>
          Create token
        </button>
      </form>
      {error && (
        <p role="alert" className="m-0 text-sm text-danger">
          {error}
        </p>
      )}

      {!isPending && tokens.length === 0 ? (
        <p className="m-0 text-sm text-muted">No tokens yet.</p>
      ) : (
        <ul className="m-0 flex list-none flex-col gap-2 p-0" aria-label="Your tokens">
          {tokens.map((t) => (
            <li
              key={t.id}
              className="flex flex-wrap items-center gap-3 border-t border-line pt-2 text-sm"
            >
              <span className="font-semibold">{t.name}</span>
              <code className="text-muted">{t.prefix}…</code>
              <span className="text-muted">
                {t.lastUsedAt
                  ? `Last used ${dateFmt.format(new Date(t.lastUsedAt))}`
                  : `Created ${dateFmt.format(new Date(t.createdAt))}, never used`}
              </span>
              <button
                type="button"
                className="btn btn-secondary ml-auto"
                aria-label={`Revoke ${t.name}`}
                onClick={() => revoke.mutate(t.id)}
              >
                Revoke
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
