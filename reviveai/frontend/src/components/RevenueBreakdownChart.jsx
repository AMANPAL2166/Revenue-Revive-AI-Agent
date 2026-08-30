import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from 'recharts'
import { formatCurrency } from '../utils/format.js'

const COLORS = {
  'Failed Payments': '#0B6E4F',
  'Checkout Abandonment': '#B45309',
  'Subscription Failures': '#2563EB',
}

export default function RevenueBreakdownChart({ breakdown, loading }) {
  if (loading || !breakdown) {
    return <div className="h-52 animate-pulse rounded-card bg-ink/5" />
  }

  const data = [
    { name: 'Failed Payments', value: Number(breakdown.failedPayments) || 0 },
    { name: 'Checkout Abandonment', value: Number(breakdown.checkoutAbandonment) || 0 },
    { name: 'Subscription Failures', value: Number(breakdown.subscriptionFailures) || 0 },
  ]
  const allZero = data.every((d) => d.value === 0)

  return (
    <div className="rounded-card border border-line bg-white p-5 shadow-card">
      <h3 className="font-display text-sm font-semibold text-ink">Revenue Leakage Breakdown</h3>
      {allZero ? (
        <div className="mt-6 py-8 text-center text-sm text-ink-muted">No revenue-risk data yet.</div>
      ) : (
        <div className="mt-4 h-52">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data} layout="vertical" margin={{ left: 8, right: 24 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#E3E6EB" horizontal={false} />
              <XAxis
                type="number"
                tickFormatter={(v) => formatCurrency(v)}
                tick={{ fontSize: 11, fill: '#5B6472' }}
                axisLine={false}
                tickLine={false}
              />
              <YAxis
                type="category"
                dataKey="name"
                width={140}
                tick={{ fontSize: 12, fill: '#0B1220' }}
                axisLine={false}
                tickLine={false}
              />
              <Tooltip
                formatter={(v) => formatCurrency(v)}
                contentStyle={{ fontSize: 12, borderRadius: 8, border: '1px solid #E3E6EB' }}
              />
              <Bar dataKey="value" radius={[0, 4, 4, 0]} barSize={22}>
                {data.map((d) => (
                  <Cell key={d.name} fill={COLORS[d.name]} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  )
}
