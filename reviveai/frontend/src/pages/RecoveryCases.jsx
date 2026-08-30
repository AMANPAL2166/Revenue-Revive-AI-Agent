import { useEffect, useState } from 'react'
import Layout from '../components/Layout.jsx'
import RecoveryQueueTable from '../components/RecoveryQueueTable.jsx'
import { api } from '../api/client.js'

const STATUS_OPTIONS = [
  'OPEN', 'ANALYZING', 'ACTION_PROPOSED', 'HUMAN_REVIEW', 'APPROVED', 'EXECUTED', 'RECOVERED', 'FAILED',
]
const PRIORITY_OPTIONS = ['HIGH', 'MEDIUM', 'LOW']

export default function RecoveryCases() {
  const [status, setStatus] = useState('')
  const [priority, setPriority] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    api
      .getRecoveryCases({ status, priority, page, size: 15 })
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
  }, [status, priority, page])

  return (
    <Layout title="Recovery Cases" subtitle="Every case the Revenue Recovery Engine has opened.">
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <FilterSelect
          label="Status"
          value={status}
          onChange={(v) => {
            setStatus(v)
            setPage(0)
          }}
          options={STATUS_OPTIONS}
        />
        <FilterSelect
          label="Priority"
          value={priority}
          onChange={(v) => {
            setPriority(v)
            setPage(0)
          }}
          options={PRIORITY_OPTIONS}
        />
      </div>

      {error && (
        <div className="mb-4 rounded-card border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
          Couldn't load recovery cases: {error}
        </div>
      )}

      <RecoveryQueueTable cases={data?.content} loading={loading} />

      {data && data.totalPages > 1 && <Pagination data={data} onPage={setPage} />}
    </Layout>
  )
}

function FilterSelect({ label, value, onChange, options }) {
  return (
    <label className="flex items-center gap-2 text-sm text-ink-muted">
      {label}
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="rounded-md border border-line bg-white px-2.5 py-1.5 text-sm text-ink"
      >
        <option value="">All</option>
        {options.map((o) => (
          <option key={o} value={o}>
            {o.replace(/_/g, ' ')}
          </option>
        ))}
      </select>
    </label>
  )
}

function Pagination({ data, onPage }) {
  return (
    <div className="mt-4 flex items-center justify-between text-sm text-ink-muted">
      <span>
        Page {data.number + 1} of {data.totalPages}
      </span>
      <div className="flex gap-2">
        <button
          disabled={data.first}
          onClick={() => onPage((p) => p - 1)}
          className="rounded-md border border-line px-3 py-1.5 hover:bg-ink/5 disabled:opacity-40"
        >
          Previous
        </button>
        <button
          disabled={data.last}
          onClick={() => onPage((p) => p + 1)}
          className="rounded-md border border-line px-3 py-1.5 hover:bg-ink/5 disabled:opacity-40"
        >
          Next
        </button>
      </div>
    </div>
  )
}
