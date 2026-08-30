import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { TrendingDown, PiggyBank, CheckCircle2, Percent } from 'lucide-react'
import Layout from '../components/Layout.jsx'
import MetricCard from '../components/MetricCard.jsx'
import RecoveryQueueTable from '../components/RecoveryQueueTable.jsx'
import RevenueBreakdownChart from '../components/RevenueBreakdownChart.jsx'
import AgentActivityFeed from '../components/AgentActivityFeed.jsx'
import { api } from '../api/client.js'
import { formatCurrency, formatPercent } from '../utils/format.js'

export default function Dashboard() {
  const [summary, setSummary] = useState(null)
  const [breakdown, setBreakdown] = useState(null)
  const [cases, setCases] = useState(null)
  const [activity, setActivity] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const [summaryRes, breakdownRes, casesRes, activityRes] = await Promise.all([
          api.getDashboardSummary(),
          api.getRevenueBreakdown(),
          api.getRecoveryCases({ size: 6 }),
          api.getAgentActivity(0, 5),
        ])
        if (cancelled) return
        setSummary(summaryRes)
        setBreakdown(breakdownRes)
        setCases(casesRes)
        setActivity(activityRes)
      } catch (e) {
        if (!cancelled) setError(e.message)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <Layout title="Revenue Overview" subtitle="Live view of revenue at risk and what ReviveAI is doing about it.">
      {error && (
        <div className="mb-6 rounded-card border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
          Couldn't load dashboard data: {error}
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          label="Revenue At Risk"
          icon={TrendingDown}
          value={loading ? '—' : formatCurrency(summary?.revenueAtRisk)}
          sublabel="Across all open cases"
        />
        <MetricCard
          label="Recoverable"
          icon={PiggyBank}
          value={loading ? '—' : formatCurrency(summary?.recoverableRevenue)}
          sublabel="Probability-weighted estimate"
        />
        <MetricCard
          label="Recovered"
          icon={CheckCircle2}
          tone="revive"
          value={loading ? '—' : formatCurrency(summary?.recoveredRevenue)}
          sublabel="Revenue actually recovered"
        />
        <MetricCard
          label="Recovery Rate"
          icon={Percent}
          value={loading ? '—' : formatPercent(summary?.recoveryRatePercent)}
          sublabel="Recovered ÷ resolved cases"
        />
      </div>

      <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="font-display text-sm font-semibold text-ink">AI Recovery Queue</h2>
            <Link to="/recovery-cases" className="text-xs font-medium text-revive-700 hover:underline">
              View all →
            </Link>
          </div>
          <RecoveryQueueTable cases={cases?.content} loading={loading} />
        </div>

        <div className="space-y-6">
          <RevenueBreakdownChart breakdown={breakdown} loading={loading} />
          <AgentActivityFeed activity={activity} loading={loading} />
        </div>
      </div>
    </Layout>
  )
}
