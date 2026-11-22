import type { Status } from '@/api/types';
import { STATUS_STYLE } from '@/lib/status';

interface StatusBadgeProps {
  status: Status;
  withDot?: boolean;
}

export function StatusBadge({ status, withDot = false }: StatusBadgeProps) {
  const s = STATUS_STYLE[status];
  return (
    <span className={`badge ${s.badge}`}>
      {withDot && <span className={`dot ${s.dot}`} aria-hidden="true" />}
      {s.label}
    </span>
  );
}
