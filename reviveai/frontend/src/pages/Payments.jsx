import { useEffect, useState } from 'react'
import Layout from '../components/Layout.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import { api } from '../api/client.js'
import { formatCurrency, formatDateTime } from '../utils/format.js'

export default function Payments() {
  const [data, setData] = useState(null)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    api
      .getPayments(page, 20)
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
    <Layout title="Payments" subtitle="All payment attempts ReviveAI has seen via Razorpay webhooks.">
      {error && (
        <div className="mb-4 rounded-card border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
          Couldn't load payments: {error}
        </div>
      )}

      <div className="overflow-hidden rounded-card border border-line bg-white shadow-card">
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-line bg-ink/[0.02] text-xs uppercase tracking-wide text-ink-muted">
              <th className="px-4 py-3 font-medium">Customer</th>
              <th className="px-4 py-3 font-medium">Amount</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 font-medium">Failure Reason</th>
              <th className="px-4 py-3 font-medium">Retries</th>
              <th className="px-4 py-3 font-medium">Updated</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              [...Array(6)].map((_, i) => (
                <tr key={i} className="border-b border-line last:border-0">
                  <td colSpan={6} className="px-4 py-3">
                    <div className="h-3 w-full max-w-xs animate-pulse rounded bg-ink/5" />
                  </td>
                </tr>
              ))
            ) : data?.content?.length ? (
              data.content.map((p) => (
                <tr key={p.id} className="border-b border-line last:border-0 hover:bg-ink/[0.02]">
                  <td className="px-4 py-3 font-medium text-ink">{p.customerName}</td>
                  <td className="px-4 py-3 font-mono tabular-nums">{formatCurrency(p.amount)}</td>
                  <td className="px-4 py-3">
                    <StatusBadge status={p.status} />
                  </td>
                  <td className="px-4 py-3 text-ink-muted">{p.failureReason || '—'}</td>
                  <td className="px-4 py-3 font-mono">{p.retryCount}</td>
                  <td className="px-4 py-3 text-ink-muted">{formatDateTime(p.updatedAt)}</td>
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan={6} className="px-4 py-10 text-center text-sm text-ink-muted">
                  No payments recorded yet.
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
