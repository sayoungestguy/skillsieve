import { Link } from 'react-router-dom';
import type { JobDto } from '@/api/types';
import { Icon } from '@/components/ui/Icon';
import { LevelLabel } from '@/components/ui/LevelDot';
import { GAP_MAX_LEVEL, LEVELS } from '@/lib/levels';
import { useRetryExtraction } from '../hooks';
import { SkillGroup } from './SkillGroup';

export function SkillsPanel({ job }: { job: JobDto }) {
  const retry = useRetryExtraction(job.id);
  const required = job.skills.filter((s) => s.importance === 'REQUIRED');
  const preferred = job.skills.filter((s) => s.importance === 'PREFERRED');
  const gaps = required.filter((s) => s.myLevel <= GAP_MAX_LEVEL);

  return (
    <section className="panel flex flex-col gap-5" aria-labelledby="sk">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="sk" className="text-lg">
          Skills in this post
        </h2>
        {job.extractionStatus === 'DONE' && (
          <span className="badge bg-green-100 text-success">
            <Icon name="check" size={14} />
            {job.skills.length} skills found
          </span>
        )}
      </div>

      {job.extractionStatus === 'PENDING' && !job.extractionError && (
        <p role="status" className="m-0 flex items-center gap-3 text-sm text-muted">
          <span className="spinner" aria-hidden="true" />
          Extracting skills…
        </p>
      )}

      {(job.extractionStatus === 'FAILED' ||
        (job.extractionStatus === 'PENDING' && job.extractionError)) && (
        <div
          role="alert"
          className="flex flex-wrap items-center gap-3 rounded-card border border-amber-200 bg-amber-50 p-4 text-sm"
        >
          <span className="flex text-warning">
            <Icon name="warning" />
          </span>
          <p className="m-0 flex-1">
            {job.extractionStatus === 'PENDING'
              ? 'Skills pending. The extractor is busy or offline, so try again later.'
              : 'Skills could not be extracted. Try again in a few minutes.'}
          </p>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => retry.mutate()}
            disabled={retry.isPending}
          >
            Retry
          </button>
        </div>
      )}

      {job.extractionStatus === 'DONE' && (
        <>
          <SkillGroup label="Required" skills={required} />
          <SkillGroup label="Preferred" skills={preferred} />
          <div className="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-chip bg-bg p-3 text-xs text-muted">
            <span className="font-semibold text-ink">My level:</span>
            {LEVELS.map((l) => (
              <LevelLabel key={l} level={l} className="text-xs" />
            ))}
            <Link to="/skills" className="ml-auto font-semibold">
              Rate my skills
            </Link>
          </div>
          {gaps.length > 0 && (
            <div className="flex items-start gap-3 rounded-card border border-amber-200 bg-amber-50 p-4">
              <span className="mt-0.5 flex text-warning">
                <Icon name="warning" />
              </span>
              <div className="text-sm">
                <p className="m-0 font-semibold">
                  {gaps.length} gap{gaps.length === 1 ? '' : 's'} for this job
                </p>
                <p className="mb-0 mt-0.5 text-muted">
                  {gaps.map((g) => g.name).join(', ')} {gaps.length === 1 ? 'is' : 'are'} required,
                  and your level is Aware or lower.
                </p>
                <Link to="/insights" className="font-semibold">
                  See all gaps
                </Link>
              </div>
            </div>
          )}
        </>
      )}

      <details className="border-t border-line pt-2">
        <summary className="flex min-h-11 cursor-pointer items-center font-semibold text-indigo-700">
          Full job post
        </summary>
        {/* Plain text only — never render job text as HTML. */}
        <p className="mt-2 whitespace-pre-line text-sm text-ink">{job.rawText}</p>
      </details>
    </section>
  );
}
