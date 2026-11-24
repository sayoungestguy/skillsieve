import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import type { JobSummaryDto, Status } from '@/api/types';
import { PageHeader } from '@/components/ui/PageHeader';
import { isDue, plural } from '@/lib/format';
import { STATUSES, STATUS_STYLE } from '@/lib/status';
import { useBoard, useMoveJob } from '../hooks';
import { BoardColumn } from './BoardColumn';

export function BoardPage() {
  const { data, isPending, isError } = useBoard();
  const move = useMoveJob();
  const [mobileStatus, setMobileStatus] = useState<Status>('SAVED');
  const jobs = data?.content ?? [];
  const byStatus = (s: Status) => jobs.filter((j) => j.status === s);
  const dueCount = jobs.filter((j) => isDue(j.nextActionAt)).length;

  const onMove = (job: JobSummaryDto, to: Status) => {
    if (job.status !== to) move.mutate({ job, to });
  };
  const onDropJob = (jobId: string, to: Status) => {
    const job = jobs.find((j) => j.id === jobId);
    if (job) onMove(job, to);
  };

  return (
    <>
      <PageHeader
        title="Board"
        subtitle={
          <>
            <span className="tabular-nums">{jobs.length}</span> {plural(jobs.length, 'job')}
            {dueCount > 0 && (
              <>
                {' · '}
                <span className="font-semibold text-danger">
                  <span className="tabular-nums">{dueCount}</span> follow-up
                  {dueCount === 1 ? '' : 's'} due
                </span>
              </>
            )}
          </>
        }
        aside={
          <p className="m-0 hidden text-xs text-muted md:block">
            Drag cards between columns, or select a card and press <kbd>Shift</kbd> + <kbd>→</kbd>
          </p>
        }
      />
      <p aria-live="polite" className="sr-only">
        {move.isSuccess && move.variables
          ? `Moved to ${STATUS_STYLE[move.variables.to].label}`
          : ''}
      </p>
      {isError && <p className="text-danger">Could not load your jobs. Refresh to try again.</p>}
      {isPending ? (
        <div className="grid grid-cols-6 gap-3" aria-hidden="true">
          {STATUSES.map((s) => (
            <div key={s} className="h-[560px] rounded-card bg-slate-100" />
          ))}
        </div>
      ) : (
        <>
          {/* Mobile: status tabs, one column at a time */}
          <div
            className="-mx-4 flex gap-2 overflow-x-auto px-4 md:hidden"
            role="group"
            aria-label="Status"
          >
            {STATUSES.map((s) => (
              <button
                key={s}
                type="button"
                aria-pressed={mobileStatus === s}
                className={`pill-tab ${mobileStatus === s ? 'pill-tab-on' : ''}`}
                onClick={() => setMobileStatus(s)}
              >
                {STATUS_STYLE[s].label}{' '}
                <span className="tabular-nums opacity-80">{byStatus(s).length}</span>
              </button>
            ))}
          </div>
          <div className="-mx-1 overflow-x-auto px-1 pb-2">
            <div className="grid grid-cols-1 gap-3 md:grid-cols-[repeat(6,minmax(176px,1fr))]">
              {STATUSES.map((s) => (
                <BoardColumn
                  key={s}
                  status={s}
                  jobs={byStatus(s)}
                  onMove={onMove}
                  onDropJob={onDropJob}
                  hiddenOnMobile={s !== mobileStatus}
                />
              ))}
            </div>
          </div>
        </>
      )}
      <Outlet />
    </>
  );
}
