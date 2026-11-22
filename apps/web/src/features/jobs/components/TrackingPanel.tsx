import { useEffect, useState } from 'react';
import type { ApplicationDto, Status } from '@/api/types';
import { Icon } from '@/components/ui/Icon';
import { isDue } from '@/lib/format';
import { STATUSES, STATUS_STYLE } from '@/lib/status';
import { useUpdateApplication } from '../hooks';

const AUTOSAVE_MS = 800;

interface TrackingPanelProps {
  application: ApplicationDto;
}

export function TrackingPanel({ application }: TrackingPanelProps) {
  const update = useUpdateApplication();
  const [notes, setNotes] = useState(application.notes ?? '');
  const save = (body: { status?: Status; nextActionAt?: string | null; notes?: string | null }) =>
    update.mutate({ appId: application.id, body: { ...body, version: application.version } });

  // Notes autosave (DESIGN.md §3.3), debounced.
  useEffect(() => {
    if (notes === (application.notes ?? '')) return;
    const id = setTimeout(() => save({ notes }), AUTOSAVE_MS);
    return () => clearTimeout(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only re-run when the text changes
  }, [notes]);

  const due = isDue(application.nextActionAt);

  return (
    <section className="panel flex flex-col gap-5" aria-labelledby="trk">
      <h2 id="trk" className="text-lg">
        Tracking
      </h2>
      <div>
        <label className="label" htmlFor="st">
          Status
        </label>
        <select
          id="st"
          className="field pr-8"
          value={application.status}
          onChange={(e) => save({ status: e.target.value as Status })}
        >
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {STATUS_STYLE[s].label}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="label" htmlFor="na">
          Next action date
        </label>
        <input
          id="na"
          className="field"
          type="date"
          value={application.nextActionAt ?? ''}
          aria-describedby={due ? 'na-due' : undefined}
          onChange={(e) => save({ nextActionAt: e.target.value || null })}
        />
        {due && (
          <p
            id="na-due"
            className="mt-1.5 flex items-center gap-1.5 text-[0.8125rem] font-semibold text-danger"
          >
            <span className="dot bg-danger" aria-hidden="true" />
            Follow-up due
          </p>
        )}
      </div>
      <div>
        <label className="label" htmlFor="nt">
          Notes
        </label>
        <textarea
          id="nt"
          className="field"
          rows={5}
          aria-describedby="nt-s"
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
        />
        <p
          id="nt-s"
          className="mt-1.5 flex items-center gap-1 text-xs text-muted"
          aria-live="polite"
        >
          {update.isPending ? (
            'Saving…'
          ) : update.isError ? (
            <span className="font-semibold text-danger">Not saved. Refresh and try again.</span>
          ) : (
            <>
              <Icon name="check" size={14} />
              {update.isSuccess ? 'Saved just now' : 'Changes save automatically'}
            </>
          )}
        </p>
      </div>
    </section>
  );
}
