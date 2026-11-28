import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '@/api/client';
import type { JobDto, SuggestionDto } from '@/api/types';
import { Icon } from '@/components/ui/Icon';
import { useRequestTailoring, useTailoring } from '../tailoring';

/** 3-5 suggested resume bullets for this job, from the model on our own server (never Gemini). */
export function TailoringPanel({ job }: { job: JobDto }) {
  const { data: tailoring, isPending } = useTailoring(job.id);
  const request = useRequestTailoring(job.id);
  const [error, setError] = useState<string | null>(null);

  const start = async () => {
    setError(null);
    try {
      await request.mutateAsync();
    } catch (err) {
      setError(err instanceof ApiError ? (err.problem.detail ?? err.message) : 'Could not start.');
    }
  };

  const running = tailoring?.status === 'PENDING';
  const canAsk = job.extractionStatus === 'DONE' && job.skills.length > 0;
  const label = tailoring ? 'Suggest again' : 'Suggest resume changes';

  return (
    <section className="panel flex flex-col gap-4" aria-labelledby="tl">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="tl" className="text-lg">
          Resume suggestions
        </h2>
        {!running && !isPending && (
          <button
            type="button"
            className={tailoring ? 'btn btn-secondary' : 'btn btn-primary'}
            onClick={start}
            disabled={!canAsk || request.isPending}
          >
            {label}
          </button>
        )}
      </div>
      <p className="m-0 text-sm text-muted">
        Rewrites of your own resume bullets that match this post. Made by a model on this server;
        your resume is never sent to Gemini. Check every line before you use it.
      </p>

      {!canAsk && (
        <p className="m-0 text-sm text-muted">Suggestions need this post&apos;s skills first.</p>
      )}

      {error && (
        <p role="alert" className="m-0 text-sm text-danger">
          {error}{' '}
          {error.includes('resume') && (
            <Link to="/settings" className="font-semibold">
              Open Settings
            </Link>
          )}
        </p>
      )}

      {running && (
        <p role="status" className="m-0 flex items-center gap-3 text-sm text-muted">
          <span className="spinner" aria-hidden="true" />
          Writing suggestions… this can take a few minutes.
        </p>
      )}

      {tailoring?.status === 'FAILED' && (
        <p role="alert" className="m-0 text-sm text-danger">
          {tailoring.error ?? 'Something went wrong. Try again.'}
        </p>
      )}

      {tailoring?.status === 'DONE' && (
        <>
          {tailoring.stale && (
            <p role="status" className="m-0 rounded-chip bg-amber-50 p-3 text-sm">
              Your resume or this post changed since these were made. Suggest again to refresh.
            </p>
          )}
          {tailoring.suggestions.length === 0 ? (
            <p className="m-0 text-sm text-muted">No suggestions this time. Try again.</p>
          ) : (
            <ol className="m-0 flex list-none flex-col gap-3 p-0" aria-label="Suggested bullets">
              {tailoring.suggestions.map((s, i) => (
                <SuggestionItem key={`${i}-${s.suggested}`} suggestion={s} />
              ))}
            </ol>
          )}
        </>
      )}
    </section>
  );
}

function SuggestionItem({ suggestion: s }: { suggestion: SuggestionDto }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    await navigator.clipboard.writeText(s.suggested);
    setCopied(true);
  };
  return (
    <li className="flex flex-col gap-2 rounded-card border border-line p-3">
      {s.original && (
        <p className="m-0 text-sm text-muted line-through decoration-slate-400">{s.original}</p>
      )}
      <div className="flex flex-wrap items-start gap-2">
        <p className="m-0 flex-1 font-semibold">{s.suggested}</p>
        <button
          type="button"
          className="btn btn-secondary"
          onClick={copy}
          aria-label={`Copy: ${s.suggested}`}
        >
          <Icon name={copied ? 'check' : 'copy'} size={16} />
          {copied ? 'Copied' : 'Copy'}
        </button>
      </div>
      {(s.skills.length > 0 || s.reason) && (
        <p className="m-0 flex flex-wrap items-center gap-2 text-xs text-muted">
          {s.skills.map((k) => (
            <span key={k} className="badge bg-indigo-50 text-indigo-700">
              {k}
            </span>
          ))}
          {s.reason}
        </p>
      )}
    </li>
  );
}
