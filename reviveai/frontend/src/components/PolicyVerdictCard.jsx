import { formatActionLabel } from '../utils/format.js'

const TONE = {
  ALLOWED: { label: 'Allowed', classes: 'border-revive-600 text-revive-700 bg-revive-50' },
  BLOCKED: { label: 'Blocked', classes: 'border-rose-600 text-rose-700 bg-rose-50' },
  REQUIRES_APPROVAL: { label: 'Requires Approval', classes: 'border-amber-600 text-amber-700 bg-amber-50' },
  PENDING_REVIEW: { label: 'Pending Review', classes: 'border-ink/30 text-ink-muted bg-ink/5' },
}

export default function PolicyVerdictCard({ aiRecommendation, policyDecision }) {
  if (!aiRecommendation || !policyDecision) {
    return (
      <div className="rounded-card border border-line bg-white p-5 shadow-card">
        <h3 className="font-display text-sm font-semibold text-ink">Policy Decision</h3>
        <p className="mt-4 text-sm text-ink-muted">No AI recommendation has been evaluated yet.</p>
      </div>
    )
  }

  const tone = TONE[policyDecision.status] || TONE.PENDING_REVIEW
  const requiresHuman = policyDecision.status === 'BLOCKED' || policyDecision.status === 'REQUIRES_APPROVAL'

  return (
    <div className="rounded-card border border-line bg-white p-5 shadow-card">
      <h3 className="font-display text-sm font-semibold text-ink">Policy Decision</h3>
      <p className="mt-1 text-xs text-ink-muted">
        The Policy Engine validates every AI-recommended action before it can run. AI decides; Policy Engine
        controls.
      </p>

      <div className="mt-4 grid grid-cols-2 gap-4 text-sm">
        <div>
          <div className="text-xs uppercase tracking-wide text-ink-muted">AI Proposed Action</div>
          <div className="mt-1 font-medium text-ink">{formatActionLabel(aiRecommendation.action)}</div>
          {aiRecommendation.action === 'OFFER_DISCOUNT' && aiRecommendation.discountPercent != null && (
            <div className="mt-0.5 font-mono text-xs text-ink-muted">
              Requested discount: {aiRecommendation.discountPercent}%
            </div>
          )}
        </div>
        <div>
          <div className="text-xs uppercase tracking-wide text-ink-muted">Reason</div>
          <div className="mt-1 text-ink-muted">{policyDecision.reason || '—'}</div>
        </div>
      </div>

      <div className="mt-6 flex flex-wrap items-center gap-4">
        <div
          className={`inline-block -rotate-6 rounded-md border-[3px] px-4 py-1.5 font-display text-lg font-bold uppercase tracking-widest ${tone.classes}`}
        >
          {tone.label}
        </div>
        {requiresHuman && (
          <span className="font-mono text-xs font-semibold uppercase tracking-wide text-ink-muted">
            Action: Human Review Required
          </span>
        )}
      </div>
    </div>
  )
}
