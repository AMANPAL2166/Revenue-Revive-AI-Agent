import { useEffect, useState } from 'react'
import Layout from '../components/Layout.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import { api } from '../api/client.js'
import { formatDateTime, formatActionLabel } from '../utils/format.js'

export default function AgentActivity() {
  const [data, setData] = useState(null)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    api
      .getAgentActivity(page, 20)
      .then((res) => {
        if (!cancelled) setData(res)
      })
      .catch((e) => {
        if (!cancelled) setError(e.message)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [page])

  return (
    <Layout title="Agent Activity" subtitle="Every AI recommendation and the policy verdict it received.">
      {error && (
        <div className="mb-4 rounded-card border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
          Couldn't load agent activity: {error}
        </div>
      )}

      <div className="overflow-hidden rounded-card border border-line bg-white shadow-card">
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-line bg-ink/[0.02] text-xs uppercase tracking-wide text-ink-muted">
              <th className="px-4 py-3 font-medium">Time</th>
              <th className="px-4 py-3 font-medium">Customer</th>
              <th className="px-4 py-3 font-medium">AI Decision</th>
              <th className="px-4 py-3 font-medium">Confidence</th>
              <th className="px-4 py-3 font-medium">Policy Decision</th>
              <th className="px-4 py-3 font-medium">Case Status</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              [...Array(8)].map((_, i) => (
                <tr key={i} className="border-b border-line last:border-0">
                  <td colSpan={6} className="px-4 py-3">
                    <div className="h-3 w-full max-w-md animate-pulse rounded bg-ink/5" />
                  </td>
                </tr>
              ))
            ) : data?.content?.length ? (
              data.content.map((a, i) => (
                <tr key={i} className="border-b border-line last:border-0 hover:bg-ink/[0.02]">
                  <td className="px-4 py-3 font-mono text-ink-muted">{formatDateTime(a.time)}</td>
                  <td className="px-4 py-3 font-medium text-ink">{a.customerName || 'Unknown'}</td>
                  <td className="px-4 py-3">{formatActionLabel(a.aiDecision)}</td>
                  <td className="px-4 py-3 font-mono tabular-nums">{Math.round(a.confidence * 100)}%</td>
                  <td className="px-4 py-3">
                    <StatusBadge status={a.policyDecision} />
                  </td>
                  <td className="px-4 py-3">
                    <StatusBadge status={a.caseStatus} />
                  </td>
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan={6} className="px-4 py-10 text-center text-sm text-ink-muted">
                  No agent activity yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {data && data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm text-ink-muted">
          <span>
            Page {data.number + 1} of {data.totalPages}
          </span>
          <div className="flex gap-2">
            <button
              disabled={data.first}
              onClick={() => setPage((p) => p - 1)}
              className="rounded-md border border-line px-3 py-1.5 hover:bg-ink/5 disabled:opacity-40"
            >
              Previous
            </button>
            <button
              disabled={data.last}
              onClick={() => setPage((p) => p + 1)}
              className="rounded-md border border-line px-3 py-1.5 hover:bg-ink/5 disabled:opacity-40"
            >
              Next
            </button>
          </div>
        </div>
      )}
    </Layout>
  )
}
