import { useState } from 'react';
import type { JobSummaryDto, Status } from '@/api/types';
import { STATUS_STYLE } from '@/lib/status';
import { JobCard } from './JobCard';

const VISIBLE_CARDS = 3;

interface BoardColumnProps {
  status: Status;
  jobs: JobSummaryDto[];
  onMove: (job: JobSummaryDto, to: Status) => void;
  onDropJob: (jobId: string, to: Status) => void;
  /** Below md only the selected status tab's column shows. */
  hiddenOnMobile?: boolean;
}

export function BoardColumn({
  status,
  jobs,
  onMove,
  onDropJob,
  hiddenOnMobile = false,
}: BoardColumnProps) {
  const [over, setOver] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const s = STATUS_STYLE[status];
  const id = `col-${status}`;
  const shown = expanded ? jobs : jobs.slice(0, VISIBLE_CARDS);
  const hidden = jobs.length - shown.length;

  return (
    <section
      aria-labelledby={id}
      className={`${hiddenOnMobile ? 'hidden md:flex' : 'flex'} min-h-[560px] flex-col gap-2.5 rounded-card p-3 ${over ? 'bg-accent-weak ring-2 ring-accent' : 'bg-slate-100'}`}
      onDragOver={(e) => {
        e.preventDefault();
        setOver(true);
      }}
      onDragLeave={() => setOver(false)}
      onDrop={(e) => {
        e.preventDefault();
        setOver(false);
        onDropJob(e.dataTransfer.getData('text/plain'), status);
      }}
    >
      <h2 id={id} className="flex items-center gap-2 px-1 pb-1.5 pt-1 text-sm">
        <span className={`dot ${s.dot}`} aria-hidden="true" />
        {s.label}
        <span className="ml-auto rounded-full border border-line bg-surface px-2 text-xs tabular-nums text-muted">
          {jobs.length}
        </span>
      </h2>
      {jobs.length === 0 && (
        <div className="rounded-card border border-dashed border-slate-300 px-3 py-6 text-center text-sm text-muted">
          Nothing here yet
        </div>
      )}
      {shown.map((job) => (
        <JobCard key={job.id} job={job} onMove={onMove} />
      ))}
      {hidden > 0 && (
        <button
          type="button"
          className="btn btn-ghost min-h-9 text-[0.8125rem]"
          onClick={() => setExpanded(true)}
        >
          +{hidden} more
        </button>
      )}
    </section>
  );
}
