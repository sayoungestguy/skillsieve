import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Icon } from '@/components/ui/Icon';
import { PageHeader } from '@/components/ui/PageHeader';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useSearch } from '../hooks';
import { plural, UNKNOWN_COMPANY, UNTITLED_ROLE } from '@/lib/format';

const DEBOUNCE_MS = 250;

export function SearchPage() {
  const [params, setParams] = useSearchParams();
  const [q, setQ] = useState(params.get('q') ?? '');
  const [term, setTerm] = useState(q);
  const { data, isFetching } = useSearch(term);

  useEffect(() => {
    const id = setTimeout(() => {
      setTerm(q);
      setParams(q ? { q } : {}, { replace: true });
    }, DEBOUNCE_MS);
    return () => clearTimeout(id);
  }, [q, setParams]);

  const hits = term.trim() ? (data?.content ?? []) : [];
  const empty = !!term.trim() && !isFetching && hits.length === 0;

  return (
    <>
      <PageHeader
        title="Search"
        subtitle="Search every post you saved: company, title, skills and full text."
      />
      <form
        role="search"
        className="relative flex max-w-[720px] items-center"
        onSubmit={(e) => e.preventDefault()}
      >
        <label className="sr-only" htmlFor="search-q">
          Search saved posts
        </label>
        <span className="absolute left-4 flex text-muted">
          <Icon name="search" size={22} />
        </span>
        <input
          id="search-q"
          className="field min-h-14 rounded-card pl-12 text-lg"
          type="search"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
      </form>
      <p className="-mt-2 text-sm text-muted" aria-live="polite">
        {term.trim() ? (
          <>
            <span className="tabular-nums">{hits.length}</span> {plural(hits.length, 'result')} for
            “{term.trim()}” ·{' '}
          </>
        ) : null}
        Press <kbd>/</kbd> anywhere to search
      </p>
      <ul className="m-0 flex max-w-[880px] list-none flex-col gap-3 p-0">
        {hits.map((r) => (
          <li key={r.jobId} className="card gap-1.5 px-5 py-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <Link
                to={`/jobs/${r.jobId}`}
                className="font-semibold text-ink no-underline hover:text-ink hover:underline"
              >
                {r.company ?? UNKNOWN_COMPANY} ·{' '}
                <span className="font-medium">{r.title ?? UNTITLED_ROLE}</span>
              </Link>
              <StatusBadge status={r.status} />
            </div>
            <p className="m-0 text-sm text-muted">
              …{r.snippet.before}
              {r.snippet.match && <mark>{r.snippet.match}</mark>}
              {r.snippet.after}…
            </p>
          </li>
        ))}
      </ul>
      {empty && (
        <div className="panel flex max-w-[880px] flex-col items-center gap-2 text-center">
          <p className="m-0 font-semibold">No posts match “{term.trim()}”</p>
          <p className="m-0 text-sm text-muted">Try a skill name like “react”, or a company.</p>
          <Link className="btn btn-primary" to="/jobs/new">
            Add a job post
          </Link>
        </div>
      )}
    </>
  );
}
