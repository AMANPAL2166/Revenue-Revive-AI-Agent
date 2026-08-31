import PriorityBadge from './PriorityBadge.jsx'
import { formatCurrency, formatPercent } from '../utils/format.js'

export default function DecisionMetricsCard({ recoveryCase }) {
  if (!recoveryCase) return null

  const rows = [
    ['Revenue At Risk', formatCurrency(recoveryCase.revenueAtRisk)],
    ['Customer Lifetime Value', formatCurrency(recoveryCase.customerLifetimeValue)],
    ['Payment Success Rate', formatPercent(recoveryCase.paymentSuccessRate, { fromFraction: true })],
    ['Recovery Probability', formatPercent(recoveryCase.recoveryProbability, { fromFraction: true })],
    ['Expected Recovery Value', formatCurrency(recoveryCase.expectedRecoveryValue)],
  ]

  return (
    <div className="rounded-card border border-line bg-white p-5 shadow-card">
      <div className="flex items-center justify-between">
        <h3 className="font-display text-sm font-semibold text-ink">Decision Metrics</h3>
        <PriorityBadge priority={recoveryCase.priority} />
      </div>
      <dl className="mt-4 space-y-3">
        {rows.map(([label, value]) => (
          <div key={label} className="flex items-center justify-between text-sm">
            <dt className="text-ink-muted">{label}</dt>
            <dd className="font-mono font-medium tabular-nums text-ink">{value}</dd>
          </div>
        ))}
      </dl>
    </div>
  )
}
