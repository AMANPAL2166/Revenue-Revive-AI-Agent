import { formatDateTime } from '../utils/format.js'

export default function CaseTimeline({ timeline }) {
  if (!timeline || timeline.length === 0) return null

  return (
    <div className="rounded-card border border-line bg-white p-5 shadow-card">
      <h3 className="font-display text-sm font-semibold text-ink">Timeline</h3>
      <ol className="mt-4 space-y-0">
        {timeline.map((entry, i) => (
          <li key={i} className="relative flex gap-4 pb-6 last:pb-0">
            {i < timeline.length - 1 && <span className="absolute left-[5px] top-3 h-full w-px bg-line" />}
            <span className="relative z-10 mt-1.5 h-[11px] w-[11px] shrink-0 rounded-full border-2 border-revive-600 bg-white" />
            <div className="min-w-0 flex-1">
              <div className="flex items-center justify-between gap-3">
                <span className="text-sm font-medium text-ink">{entry.label}</span>
                <span className="shrink-0 font-mono text-xs text-ink-muted">{formatDateTime(entry.timestamp)}</span>
              </div>
              {entry.description && <p className="mt-0.5 text-xs text-ink-muted">{entry.description}</p>}
            </div>
          </li>
        ))}
      </ol>
    </div>
  )
}
