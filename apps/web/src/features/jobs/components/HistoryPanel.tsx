import { formatDateTime } from '@/lib/format';
import { STATUS_STYLE } from '@/lib/status';
import { useHistory } from '../hooks';

export function HistoryPanel({ applicationId }: { applicationId: string }) {
  const { data = [] } = useHistory(applicationId);
  return (
    <section className="panel" aria-labelledby="hist">
      <h2 id="hist" className="mb-4 text-lg">
        Status history
      </h2>
      <ol className="m-0 flex list-none flex-col p-0">
        {data.map((e, i) => (
          <li key={`${e.toStatus}-${e.changedAt}`} className="flex gap-3">
            <span className="flex flex-col items-center">
              <span
                className={`dot mt-[5px] h-3 w-3 ${STATUS_STYLE[e.toStatus].dot}`}
                aria-hidden="true"
              />
              {i < data.length - 1 && <span className="w-0.5 flex-1 bg-line" />}
            </span>
            <span className={i < data.length - 1 ? 'pb-4' : ''}>
              <span className="block text-[0.9375rem] font-semibold">
                {STATUS_STYLE[e.toStatus].label}
              </span>
              <span className="text-xs text-muted">{formatDateTime(e.changedAt)}</span>
            </span>
          </li>
        ))}
      </ol>
    </section>
  );
}
