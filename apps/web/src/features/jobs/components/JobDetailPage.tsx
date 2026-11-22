import { Link, useParams } from 'react-router-dom';
import { Icon } from '@/components/ui/Icon';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { formatDate, hostOf } from '@/lib/format';
import { useJob } from '../hooks';
import { HistoryPanel } from './HistoryPanel';
import { SkillsPanel } from './SkillsPanel';
import { TrackingPanel } from './TrackingPanel';

export function JobDetailPage() {
  const { id = '' } = useParams();
  const { data: job, isPending, isError } = useJob(id);

  if (isPending) return <p className="text-muted">Loading…</p>;
  if (isError || !job)
    return (
      <div className="panel text-center">
        <p className="m-0 font-semibold">We could not find this job.</p>
        <Link to="/">Back to board</Link>
      </div>
    );

  return (
    <>
      <Link to="/" className="btn btn-ghost -ml-3 self-start">
        <Icon name="back" size={18} />
        Board
      </Link>
      <div className="-mt-3 flex flex-wrap items-start justify-between gap-4">
        <div className="flex flex-col gap-1.5">
          <p className="m-0 font-semibold text-muted">{job.company ?? 'Unknown company'}</p>
          <h1 className="text-[1.875rem]">{job.title ?? 'Untitled role'}</h1>
          <p className="m-0 flex flex-wrap items-center gap-x-4 gap-y-1.5 text-sm text-muted">
            {job.location && <span>{job.location}</span>}
            <span>Saved {formatDate(job.createdAt)}</span>
            {job.sourceUrl && (
              <a
                href={job.sourceUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1 font-semibold"
              >
                {hostOf(job.sourceUrl)} <Icon name="external" size={14} />
                <span className="sr-only">(opens in new tab)</span>
              </a>
            )}
          </p>
        </div>
        <StatusBadge status={job.application.status} withDot />
      </div>
      <div className="flex flex-wrap items-start gap-6">
        <div className="flex min-w-0 flex-[1_1_360px] flex-col gap-6">
          <TrackingPanel key={job.application.id} application={job.application} />
          <HistoryPanel applicationId={job.application.id} />
        </div>
        <div className="min-w-0 flex-[1.4_1_420px]">
          <SkillsPanel job={job} />
        </div>
      </div>
    </>
  );
}
