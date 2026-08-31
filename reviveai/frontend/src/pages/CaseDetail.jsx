import { useCallback, useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { ArrowLeft, RefreshCw, Check, X, Play } from 'lucide-react'
import Layout from '../components/Layout.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import PriorityBadge from '../components/PriorityBadge.jsx'
import DecisionMetricsCard from '../components/DecisionMetricsCard.jsx'
import AiRecommendationCard from '../components/AiRecommendationCard.jsx'
import PolicyVerdictCard from '../components/PolicyVerdictCard.jsx'
import ExecutionResultCard from '../components/ExecutionResultCard.jsx'
import CaseTimeline from '../components/CaseTimeline.jsx'
import { api } from '../api/client.js'
import { formatCurrency, formatDateTime } from '../utils/format.js'

export default function CaseDetail() {
  const { id } = useParams()
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [actionLoading, setActionLoading] = useState(null)
  const [actionError, setActionError] = useState(null)
  const [confirmingReject, setConfirmingReject] = useState(false)

  const load = useCallback(() => {
    setLoading(true)
    api
      .getRecoveryCase(id)
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [id])

  useEffect(() => {
    load()
  }, [load])

  async function runAction(name, fn) {
    setActionLoading(name)
    setActionError(null)
    try {
      const updated = await fn()
      setData(updated)
      setConfirmingReject(false)
    } catch (e) {
      setActionError(e.message)
    } finally {
      setActionLoading(null)
    }
  }

  if (loading) {
    return (
      <Layout title="Recovery Case" subtitle={id}>
        <div className="h-64 animate-pulse rounded-card bg-ink/5" />
      </Layout>
    )
  }

  if (error || !data) {
    return (
      <Layout title="Recovery Case" subtitle={id}>
        <div className="rounded-card border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
          Couldn't load this case: {error}
        </div>
      </Layout>
    )
  }

  const canApproveReject = data.status === 'HUMAN_REVIEW'
  const canExecute = data.status === 'APPROVED'

  return (
    <Layout
      title={data.customer?.name || 'Recovery Case'}
      subtitle={`${formatCurrency(data.revenueAtRisk)} at risk · opened ${formatDateTime(data.createdAt)}`}
      actions={
        <div className="flex items-center gap-2">
          <StatusBadge status={data.status} />
          <PriorityBadge priority={data.priority} />
        </div>
      }
    >
      <Link to="/recovery-cases" className="mb-4 inline-flex items-center gap-1.5 text-sm text-ink-muted hover:text-ink">
        <ArrowLeft className="h-3.5 w-3.5" /> Back to Recovery Cases
      </Link>

      {actionError && (
        <div className="mb-4 rounded-card border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
          {actionError}
        </div>
      )}

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <button
          onClick={() => runAction('analyze', () => api.analyzeCase(id))}
          disabled={actionLoading !== null}
          className="inline-flex items-center gap-1.5 rounded-md border border-line bg-white px-3 py-1.5 text-sm font-medium text-ink hover:bg-ink/5 disabled:opacity-50"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${actionLoading === 'analyze' ? 'animate-spin' : ''}`} />
          Re-run Analysis
        </button>

        {canExecute && (
          <button
            onClick={() => runAction('execute', () => api.executeCase(id))}
            disabled={actionLoading !== null}
            className="inline-flex items-center gap-1.5 rounded-md bg-ink px-3 py-1.5 text-sm font-medium text-white hover:bg-ink/90 disabled:opacity-50"
          >
            <Play className="h-3.5 w-3.5" /> Execute
          </button>
        )}

        {canApproveReject && (
          <>
            <button
              onClick={() => runAction('approve', () => api.approveCase(id))}
              disabled={actionLoading !== null}
              className="inline-flex items-center gap-1.5 rounded-md bg-revive-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-revive-700 disabled:opacity-50"
            >
              <Check className="h-3.5 w-3.5" /> Approve
            </button>
            {!confirmingReject ? (
              <button
                onClick={() => setConfirmingReject(true)}
                disabled={actionLoading !== null}
                className="inline-flex items-center gap-1.5 rounded-md border border-rose-200 bg-rose-50 px-3 py-1.5 text-sm font-medium text-rose-700 hover:bg-rose-100 disabled:opacity-50"
              >
                <X className="h-3.5 w-3.5" /> Reject
              </button>
            ) : (
              <span className="inline-flex items-center gap-2 text-sm">
                <span className="text-ink-muted">Reject this case?</span>
                <button
                  onClick={() => runAction('reject', () => api.rejectCase(id))}
                  className="rounded-md bg-rose-600 px-2.5 py-1 text-xs font-medium text-white hover:bg-rose-700"
                >
                  Confirm
                </button>
                <button
                  onClick={() => setConfirmingReject(false)}
                  className="rounded-md border border-line px-2.5 py-1 text-xs text-ink-muted hover:bg-ink/5"
                >
                  Cancel
                </button>
              </span>
            )}
          </>
        )}
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <AiRecommendationCard aiRecommendation={data.aiRecommendation} />
          <PolicyVerdictCard aiRecommendation={data.aiRecommendation} policyDecision={data.policyDecision} />
          <ExecutionResultCard executionResult={data.executionResult} />
          <CaseTimeline timeline={data.timeline} />
        </div>
        <div className="space-y-6">
          <DecisionMetricsCard recoveryCase={data} />
          <CustomerCard customer={data.customer} />
          <PaymentCard payment={data.payment} />
        </div>
      </div>
    </Layout>
  )
}

function CustomerCard({ customer }) {
  if (!customer) return null
  return (
    <div className="rounded-card border border-line bg-white p-5 shadow-card">
      <h3 className="font-display text-sm font-semibold text-ink">Customer</h3>
      <dl className="mt-3 space-y-2 text-sm">
        <Row label="Name" value={customer.name} />
        <Row label="Email" value={customer.email} mono />
        <Row label="Successful Payments" value={customer.successfulPayments} />
        <Row label="Failed Payments" value={customer.failedPayments} />
      </dl>
    </div>
  )
}

function PaymentCard({ payment }) {
  if (!payment) return null
  return (
    <div className="rounded-card border border-line bg-white p-5 shadow-card">
      <h3 className="font-display text-sm font-semibold text-ink">Payment</h3>
      <dl className="mt-3 space-y-2 text-sm">
        <Row label="Razorpay ID" value={payment.externalPaymentId} mono />
        <Row label="Amount" value={formatCurrency(payment.amount)} mono />
        <Row label="Failure Reason" value={payment.failureReason || '—'} />
        <Row label="Retry Count" value={payment.retryCount} />
      </dl>
    </div>
  )
}

function Row({ label, value, mono }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <dt className="text-ink-muted">{label}</dt>
      <dd className={`truncate text-right font-medium text-ink ${mono ? 'font-mono' : ''}`}>{value}</dd>
    </div>
  )
}
