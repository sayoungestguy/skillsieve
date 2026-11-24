import type { KeyboardEvent } from 'react';
import { Link } from 'react-router-dom';
import type { JobSummaryDto, Status } from '@/api/types';
import { Icon } from '@/components/ui/Icon';
import { formatDate, isDue, UNKNOWN_COMPANY, UNTITLED_ROLE } from '@/lib/format';
import { STATUS_STYLE, adjacentStatus } from '@/lib/status';

interface JobCardProps {
  job: JobSummaryDto;
  onMove: (job: JobSummaryDto, to: Status) => void;
}

const DATE_VERB: Record<Status, string> = {
  SAVED: 'Saved',
  APPLIED: 'Applied',
  INTERVIEW: 'Updated',
  OFFER: 'Offer',
  REJECTED: 'Closed',
  WITHDRAWN: 'Closed',
};

export function JobCard({ job, onMove }: JobCardProps) {
  const company = job.company ?? UNKNOWN_COMPANY;
  const onKeyDown = (e: KeyboardEvent) => {
    if (!e.shiftKey || (e.key !== 'ArrowRight' && e.key !== 'ArrowLeft')) return;
    e.preventDefault();
    const to = adjacentStatus(job.status, e.key === 'ArrowRight' ? 1 : -1);
    if (to) onMove(job, to);
  };

  return (
    <article
      className="card motion-safe:transition-colors"
      draggable
      onDragStart={(e) => e.dataTransfer.setData('text/plain', job.id)}
    >
      <div className="flex items-start justify-between gap-2">
        <Link
          to={`/jobs/${job.id}`}
          className="flex min-w-0 flex-col text-ink no-underline hover:text-ink hover:underline"
        >
          <span className="font-semibold">{company}</span>
          <span className="text-sm text-ink">{job.title ?? UNTITLED_ROLE}</span>
        </Link>
        <button
          type="button"
          className="icon-btn -mr-1.5 -mt-1.5 h-8 w-8 cursor-grab"
          aria-label={`Move ${company} card, now in ${STATUS_STYLE[job.status].label}. Press Shift and arrow keys to change column`}
          onKeyDown={onKeyDown}
        >
          <Icon name="grip" size={16} />
        </button>
      </div>
      <div className="flex flex-wrap items-center gap-1">
        {job.extractionStatus === 'PENDING' ? (
          <span className="inline-flex items-center gap-2 text-xs text-muted">
            <span className="spinner h-3.5 w-3.5" aria-hidden="true" />
            Extracting skills…
          </span>
        ) : job.extractionStatus === 'FAILED' ? (
          <span className="text-xs font-semibold text-warning">Skills not extracted</span>
        ) : (
          job.topSkills.map((s) => (
            <span key={s} className="chip">
              {s}
            </span>
          ))
        )}
      </div>
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs text-muted">
          {DATE_VERB[job.status]} {formatDate(job.updatedAt)}
        </span>
        {isDue(job.nextActionAt) && (
          <span
            title="Follow-up due"
            className="inline-flex items-center gap-1 text-xs font-semibold text-danger"
          >
            <span className="dot bg-danger" aria-hidden="true" />
            Follow up
          </span>
        )}
      </div>
    </article>
  );
}
