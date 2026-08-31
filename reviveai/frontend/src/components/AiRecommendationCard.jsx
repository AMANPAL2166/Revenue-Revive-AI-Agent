import { formatActionLabel } from '../utils/format.js'

export default function AiRecommendationCard({ aiRecommendation }) {
  return (
    <div className="rounded-card border border-line bg-white p-5 shadow-card">
      <h3 className="font-display text-sm font-semibold text-ink">AI Recommendation</h3>
      {!aiRecommendation ? (
        <p className="mt-4 text-sm text-ink-muted">No recommendation yet — run analysis to get one.</p>
      ) : (
        <>
          <div className="mt-3 flex items-baseline gap-3">
            <span className="font-display text-xl font-bold uppercase tracking-wide text-ink">
              {formatActionLabel(aiRecommendation.action)}
            </span>
            <span className="font-mono text-sm text-ink-muted">
              Confidence: {Math.round(aiRecommendation.confidence * 100)}%
            </span>
          </div>

          {aiRecommendation.action === 'OFFER_DISCOUNT' && aiRecommendation.discountPercent != null && (
            <div className="mt-1 font-mono text-sm text-ink-muted">
              Requested discount: {aiRecommendation.discountPercent}%
            </div>
          )}
          {aiRecommendation.action === 'RETRY_PAYMENT' && aiRecommendation.suggestedDelayHours != null && (
            <div className="mt-1 font-mono text-sm text-ink-muted">
              Suggested delay: {aiRecommendation.suggestedDelayHours}h
            </div>
          )}

          <p className="mt-3 border-l-2 border-line pl-3 text-sm italic text-ink-muted">
            "{aiRecommendation.reasoning}"
          </p>
        </>
      )}
    </div>
  )
}
