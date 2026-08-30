import StatusBadge from './StatusBadge.jsx'
import { formatRelative, formatActionLabel } from '../utils/format.js'

export default function AgentActivityFeed({ activity, loading }) {
  if (loading) {
    return (
      <div className="space-y-3 rounded-card border border-line bg-white p-5 shadow-card">
        {[...Array(4)].map((_, i) => (
          <div key={i} className="h-10 animate-pulse rounded bg-ink/5" />
        ))}
      </div>
    )
  }

  const items = activity?.content || []

  return (
    <div className="rounded-card border border-line bg-white p-5 shadow-card">
      <h3 className="font-display text-sm font-semibold text-ink">Recent Agent Activity</h3>
      {items.length === 0 ? (
        <div className="mt-6 py-6 text-center text-sm text-ink-muted">No agent activity yet.</div>
      ) : (
        <ul className="mt-3 divide-y divide-line">
          {items.map((item, i) => (
            <li key={i} className="flex items-center justify-between gap-3 py-3 text-sm">
              <div className="min-w-0">
                <div className="truncate font-medium text-ink">{item.customerName || 'Unknown customer'}</div>
                <div className="font-mono text-xs text-ink-muted">
                  {formatActionLabel(item.aiDecision)} · {formatRelative(item.time)}
                </div>
              </div>
              <StatusBadge status={item.policyDecision} />
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
