export default function MetricCard({ label, value, sublabel, tone = 'default', icon: Icon }) {
  return (
    <div className="rounded-card border border-line bg-white p-5 shadow-card">
      <div className="flex items-center justify-between">
        <span className="text-xs font-medium uppercase tracking-wide text-ink-muted">{label}</span>
        {Icon && <Icon className="h-4 w-4 text-ink-muted" strokeWidth={1.75} />}
      </div>
      <div
        className={`mt-2 font-display text-2xl font-semibold tabular-nums ${
          tone === 'revive' ? 'text-revive-700' : 'text-ink'
        }`}
      >
        {value}
      </div>
      {sublabel && <div className="mt-1 text-xs text-ink-muted">{sublabel}</div>}
    </div>
  )
}
