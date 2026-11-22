interface SummaryCardProps {
  label: string;
  value: string | number;
  note: string;
}

export function SummaryCard({ label, value, note }: SummaryCardProps) {
  return (
    <div className="panel flex flex-col gap-1 p-5">
      <span className="text-sm font-medium text-muted">{label}</span>
      <span className="text-[1.875rem] font-bold leading-tight tabular-nums">{value}</span>
      <span className="text-xs text-muted">{note}</span>
    </div>
  );
}
