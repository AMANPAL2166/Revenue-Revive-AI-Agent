import { CheckCircle2, XCircle, Clock } from 'lucide-react'
import { formatDateTime } from '../utils/format.js'

export default function ExecutionResultCard({ executionResult }) {
  return (
    <div className="rounded-card border border-line bg-white p-5 shadow-card">
      <h3 className="font-display text-sm font-semibold text-ink">Execution Result</h3>
      {!executionResult ? (
        <div className="mt-4 flex items-center gap-2 text-sm text-ink-muted">
          <Clock className="h-4 w-4" strokeWidth={1.75} />
          Not yet executed.
        </div>
      ) : (
        <div className="mt-4 space-y-2 text-sm">
          <div className="flex items-center gap-2">
            {executionResult.success ? (
              <CheckCircle2 className="h-4 w-4 text-revive-700" strokeWidth={1.75} />
            ) : (
              <XCircle className="h-4 w-4 text-rose-700" strokeWidth={1.75} />
            )}
            <span className="font-medium text-ink">{executionResult.message}</span>
          </div>
          <div className="flex items-center gap-3 pl-6 text-xs text-ink-muted">
            <span className="font-mono">{formatDateTime(executionResult.executedAt)}</span>
            {executionResult.simulated && (
              <span className="rounded bg-ink/5 px-1.5 py-0.5 font-mono uppercase tracking-wide">Simulated</span>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
