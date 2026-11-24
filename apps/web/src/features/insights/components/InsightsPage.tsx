import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { Status } from '@/api/types';
import { Icon } from '@/components/ui/Icon';
import { PageHeader } from '@/components/ui/PageHeader';
import { GAP_MIN_PCT, LEVEL_LABEL } from '@/lib/levels';
import { STATUS_STYLE } from '@/lib/status';
import { useInsightSummary, usePostsWithSkill, useSkillInsights } from '../hooks';
import { BAR_GRID, SkillBarRow } from './SkillBarRow';
import { SummaryCard } from './SummaryCard';
import { plural } from '@/lib/format';

const FILTER_STATUSES: Status[] = ['SAVED', 'APPLIED', 'INTERVIEW'];
const RANGES = [
  { value: '90', label: 'Last 90 days' },
  { value: '30', label: 'Last 30 days' },
  { value: '', label: 'All time' },
];

const daysAgoIso = (days: string) =>
  days ? new Date(Date.now() - Number(days) * 86_400_000).toISOString().slice(0, 10) : undefined;

export function InsightsPage() {
  const [status, setStatus] = useState<Status | ''>('');
  const [range, setRange] = useState('90');
  const { data: rows = [] } = useSkillInsights({
    status: status || undefined,
    from: daysAgoIso(range),
  });
  const { data: summary } = useInsightSummary();
  const [selSlug, setSelSlug] = useState<string | null>(null);
  const selected = rows.find((r) => r.slug === selSlug) ?? rows.find((r) => r.isGap) ?? rows[0];
  const { data: posts } = usePostsWithSkill(selected?.name);
  const gaps = rows.filter((r) => r.isGap);
  const topGap = gaps[0];
  const rangeLabel = RANGES.find((r) => r.value === range)?.label ?? '';
  const total = summary?.total ?? 0;

  return (
    <>
      <PageHeader
        title="Insights"
        subtitle="Which skills show up most in the jobs you saved, and where you stand."
        aside={
          <div className="flex flex-wrap items-end gap-3">
            <div>
              <label className="label" htmlFor="f-st">
                Status
              </label>
              <select
                id="f-st"
                className="field w-[180px] pr-8"
                value={status}
                onChange={(e) => setStatus(e.target.value as Status | '')}
              >
                <option value="">All statuses</option>
                {FILTER_STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {STATUS_STYLE[s].label}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="label" htmlFor="f-dt">
                Date range
              </label>
              <select
                id="f-dt"
                className="field w-[180px] pr-8"
                value={range}
                onChange={(e) => setRange(e.target.value)}
              >
                {RANGES.map((r) => (
                  <option key={r.label} value={r.value}>
                    {r.label}
                  </option>
                ))}
              </select>
            </div>
          </div>
        }
      />

      {summary && (
        <div className="grid grid-cols-[repeat(auto-fit,minmax(200px,1fr))] gap-4">
          <SummaryCard label="Total posts" value={total} note={rangeLabel} />
          <SummaryCard
            label="Applied"
            value={summary.applied}
            note={`${total ? Math.round((summary.applied / total) * 100) : 0}% of saved posts`}
          />
          <SummaryCard
            label="Interview rate"
            value={`${Math.round(summary.interviewRate * 100)}%`}
            note={`${summary.interviews} of ${summary.applied} applied`}
          />
          <SummaryCard
            label="Top gap"
            value={topGap?.name ?? '—'}
            note={
              topGap
                ? `In ${topGap.totalPct}% of posts · you: ${LEVEL_LABEL[topGap.myLevel]}`
                : 'No gaps right now'
            }
          />
        </div>
      )}

      {gaps.length > 0 && (
        <div className="flex flex-wrap items-center gap-3 rounded-card border border-amber-200 bg-amber-50 px-5 py-4">
          <span className="inline-flex items-center gap-2 font-semibold">
            <span className="flex text-warning">
              <Icon name="warning" />
            </span>
            Top gaps
          </span>
          <span className="flex flex-wrap gap-2">
            {gaps.slice(0, 4).map((g) => (
              <span
                key={g.slug}
                className="chip chip-lg border border-amber-300 bg-surface text-amber-800"
              >
                {g.name}
              </span>
            ))}
          </span>
          <Link to="/skills" className="btn btn-secondary ml-auto">
            <Icon name="bookmark" size={18} />
            Rate these skills
          </Link>
        </div>
      )}

      <div className="flex flex-wrap items-start gap-6">
        <section className="panel min-w-0 flex-[999_1_620px] px-3 py-5" aria-labelledby="top">
          <div className="flex flex-wrap items-center justify-between gap-3 px-3 pb-3">
            <h2 id="top" className="text-lg">
              Top skills{' '}
              <span className="text-sm font-normal text-muted">
                · based on <span className="tabular-nums">{total}</span> {plural(total, 'post')}
              </span>
            </h2>
            <span className="flex items-center gap-4 text-xs text-muted">
              <span className="inline-flex items-center gap-1.5">
                <span className="h-2.5 w-3.5 rounded-sm bg-accent" />
                Required
              </span>
              <span className="inline-flex items-center gap-1.5">
                <span className="h-2.5 w-3.5 rounded-sm bg-indigo-300" />
                Preferred
              </span>
              <span>Gap = in {GAP_MIN_PCT}%+ of posts and you are Aware or lower</span>
            </span>
          </div>
          <div className="overflow-x-auto">
            <div className="min-w-[560px]">
              <div
                className={`${BAR_GRID} min-h-8 px-3 text-xs font-semibold uppercase tracking-[.06em] text-muted`}
                aria-hidden="true"
              >
                <span>Skill</span>
                <span>% of posts</span>
                <span className="text-right">Total</span>
                <span>Me</span>
                <span />
              </div>
              {rows.map((r) => (
                <SkillBarRow
                  key={r.slug}
                  row={r}
                  selected={r.slug === selected?.slug}
                  onSelect={() => setSelSlug(r.slug)}
                />
              ))}
              {rows.length === 0 && (
                <p className="px-3 text-sm text-muted">
                  Save a few job posts to see your skill gaps.
                </p>
              )}
            </div>
          </div>
        </section>
        {selected && (
          <aside
            className="panel flex flex-[1_1_280px] flex-col gap-3 p-5"
            aria-labelledby="pp"
            aria-live="polite"
          >
            <h2 id="pp" className="text-lg">
              Posts with {selected.name}
            </h2>
            <p className="m-0 text-sm text-muted">
              <span className="tabular-nums">{posts?.totalElements ?? 0}</span> of{' '}
              <span className="tabular-nums">{total}</span> posts
            </p>
            <ul className="m-0 flex list-none flex-col gap-2 p-0">
              {posts?.content.map((p) => (
                <li key={p.jobId}>
                  <Link
                    to={`/jobs/${p.jobId}`}
                    className="card p-3 text-sm font-medium text-ink no-underline hover:text-ink hover:underline"
                  >
                    {p.company} · {p.title}
                  </Link>
                </li>
              ))}
            </ul>
            <Link
              to={`/search?q=${encodeURIComponent(selected.name)}`}
              className="text-sm font-semibold"
            >
              See all posts
            </Link>
          </aside>
        )}
      </div>
    </>
  );
}
