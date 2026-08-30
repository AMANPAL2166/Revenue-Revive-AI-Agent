const PRIORITY_STYLES = {
  HIGH: 'bg-rose-50 text-rose-700 ring-rose-200',
  MEDIUM: 'bg-amber-50 text-amber-700 ring-amber-200',
  LOW: 'bg-ink/5 text-ink-muted ring-ink/10',
}

export default function PriorityBadge({ priority }) {
  if (!priority) return <span className="text-ink-muted">—</span>
  return (
    <span
      className={`inline-flex items-center rounded px-2 py-0.5 text-[11px] font-semibold uppercase tracking-wide font-mono ring-1 ring-inset ${
        PRIORITY_STYLES[priority] || PRIORITY_STYLES.LOW
      }`}
    >
      {priority}
    </span>
  )
}
