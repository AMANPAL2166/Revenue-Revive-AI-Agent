import { Link } from 'react-router-dom'
import StatusBadge from './StatusBadge.jsx'
import PriorityBadge from './PriorityBadge.jsx'
import { formatCurrency, formatDateTime, formatActionLabel } from '../utils/format.js'

export default function RecoveryQueueTable({ cases, loading, emptyMessage = 'No recovery cases yet.' }) {
  if (loading) {
    return <TableSkeleton />
  }

  if (!cases || cases.length === 0) {
    return (
      <div className="rounded-card border border-dashed border-line bg-white/50 p-10 text-center text-sm text-ink-muted">
        {emptyMessage}
      </div>
    )
  }

  return (
    <div className="overflow-hidden rounded-card border border-line bg-white shadow-card">
      <table className="w-full text-left text-sm">
        <thead>
          <tr className="border-b border-line bg-ink/[0.02] text-xs uppercase tracking-wide text-ink-muted">
            <th className="px-4 py-3 font-medium">Customer</th>
            <th className="px-4 py-3 font-medium">Amount</th>
            <th className="px-4 py-3 font-medium">Priority</th>
            <th className="px-4 py-3 font-medium">Recovery Prob.</th>
            <th className="px-4 py-3 font-medium">Recommended Action</th>
            <th className="px-4 py-3 font-medium">Status</th>
            <th className="px-4 py-3 font-medium">Created</th>
          </tr>
        </thead>
        <tbody>
          {cases.map((c) => (
            <tr key={c.id} className="border-b border-line transition-colors last:border-0 hover:bg-ink/[0.02]">
              <td className="px-4 py-3">
                <Link to={`/recovery-cases/${c.id}`} className="font-medium text-ink hover:text-revive-700">
                  {c.customerName || 'Unknown customer'}
                </Link>
              </td>
              <td className="px-4 py-3 font-mono tabular-nums">{formatCurrency(c.amount)}</td>
              <td className="px-4 py-3">
                <PriorityBadge priority={c.priority} />
              </td>
              <td className="px-4 py-3 font-mono tabular-nums">
                {c.recoveryProbabilityPercent != null ? `${c.recoveryProbabilityPercent}%` : '—'}
              </td>
              <td className="px-4 py-3 text-ink-muted">{formatActionLabel(c.recommendedAction)}</td>
              <td className="px-4 py-3">
                <StatusBadge status={c.status} />
              </td>
              <td className="px-4 py-3 text-ink-muted">{formatDateTime(c.createdAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function TableSkeleton() {
  return (
    <div className="rounded-card border border-line bg-white p-4 shadow-card">
      {[...Array(5)].map((_, i) => (
        <div key={i} className="flex items-center gap-4 border-b border-line py-3 last:border-0">
          <div className="h-3 w-28 animate-pulse rounded bg-ink/5" />
          <div className="h-3 w-16 animate-pulse rounded bg-ink/5" />
          <div className="h-3 w-14 animate-pulse rounded bg-ink/5" />
          <div className="h-3 w-20 animate-pulse rounded bg-ink/5" />
          <div className="h-3 w-24 animate-pulse rounded bg-ink/5" />
        </div>
      ))}
    </div>
  )
}
