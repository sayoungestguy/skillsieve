import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '@/components/ui/PageHeader';
import { formatCategory } from '@/lib/format';
import { isGap } from '@/lib/levels';
import { useSetLevel, useSkills } from '../hooks';
import { LevelControl } from './LevelControl';

type Sort = 'demand' | 'az';

export function MySkillsPage() {
  const { data: skills = [] } = useSkills();
  const setLevel = useSetLevel();
  const [q, setQ] = useState('');
  const [sort, setSort] = useState<Sort>('demand');

  const items = useMemo(() => {
    const ql = q.trim().toLowerCase();
    return skills
      .filter((s) => !ql || s.name.toLowerCase().includes(ql))
      .sort((a, b) =>
        sort === 'az' ? a.name.localeCompare(b.name) : b.frequencyPct - a.frequencyPct,
      );
  }, [skills, q, sort]);
  const gapCount = skills.filter((s) => isGap(s.frequencyPct, s.myLevel)).length;

  return (
    <>
      <PageHeader
        title="My skills"
        subtitle="Rate your level. Insights uses this to find your gaps."
        aside={
          <p className="m-0 inline-flex items-center gap-2 text-sm">
            <span className="gap-badge">{gapCount} GAPS</span>
            <span className="text-muted">in-demand skills at Aware or lower</span>
          </p>
        }
      />
      <section className="panel p-0" aria-labelledby="ms-h">
        <h2 id="ms-h" className="sr-only">
          Skill list
        </h2>
        <div className="flex flex-wrap items-end gap-3 border-b border-line p-5">
          <div className="max-w-[420px] flex-[1_1_260px]">
            <label className="label" htmlFor="ms-q">
              Find a skill
            </label>
            <input
              id="ms-q"
              className="field"
              type="search"
              placeholder="e.g. Docker"
              value={q}
              onChange={(e) => setQ(e.target.value)}
            />
          </div>
          <div role="group" aria-label="Sort" className="ml-auto flex gap-2">
            {(
              [
                ['demand', 'Most in demand'],
                ['az', 'A–Z'],
              ] as const
            ).map(([value, label]) => (
              <button
                key={value}
                type="button"
                aria-pressed={sort === value}
                className={`pill-tab ${sort === value ? 'pill-tab-on' : ''}`}
                onClick={() => setSort(value)}
              >
                {label}
              </button>
            ))}
          </div>
        </div>
        <ul className="m-0 list-none p-0">
          {items.map((s) => (
            <li
              key={s.slug}
              className="flex flex-wrap items-center gap-x-6 gap-y-3 border-b border-line px-5 py-3"
            >
              <div className="min-w-0 flex-[1_1_220px]">
                <div className="flex items-center gap-2">
                  <span className="font-semibold">{s.name}</span>
                  {isGap(s.frequencyPct, s.myLevel) && <span className="gap-badge">GAP</span>}
                </div>
                <div className="text-xs text-muted">
                  {formatCategory(s.category)} · in{' '}
                  <span className="tabular-nums">{s.frequencyPct}%</span> of posts
                </div>
              </div>
              <div className="max-w-[200px] flex-[1_1_160px]" aria-hidden="true">
                <div className="flex h-2 overflow-hidden rounded bg-slate-100">
                  <span
                    className="block h-full bg-neutral"
                    style={{ width: `${s.frequencyPct}%` }}
                  />
                </div>
              </div>
              <LevelControl
                skillName={s.name}
                slug={s.slug}
                value={s.myLevel}
                onChange={(level) => setLevel.mutate({ slug: s.slug, level })}
              />
            </li>
          ))}
          {items.length === 0 && (
            <li className="px-5 py-6 text-sm text-muted">No skills match “{q}”.</li>
          )}
        </ul>
        <p className="m-0 px-5 py-4 text-xs text-muted">
          Showing skills seen in your posts plus the skill list.{' '}
          <Link to="/insights">See gaps in Insights</Link>
        </p>
      </section>
    </>
  );
}
