const STATUS_STYLES = {
  // RecoveryCaseStatus
  OPEN: { label: 'Open', tone: 'neutral' },
  ANALYZING: { label: 'Analyzing', tone: 'info' },
  ACTION_PROPOSED: { label: 'Action Proposed', tone: 'info' },
  BLOCKED: { label: 'Blocked', tone: 'danger' },
  HUMAN_REVIEW: { label: 'Human Review', tone: 'warning' },
  APPROVED: { label: 'Approved', tone: 'info' },
  EXECUTED: { label: 'Executed', tone: 'info' },
  RECOVERED: { label: 'Recovered', tone: 'success' },
  FAILED: { label: 'Failed', tone: 'danger' },

  // PolicyStatus
  PENDING_REVIEW: { label: 'Pending Review', tone: 'neutral' },
  ALLOWED: { label: 'Allowed', tone: 'success' },
  REQUIRES_APPROVAL: { label: 'Requires Approval', tone: 'warning' },

  // PaymentStatus
  CREATED: { label: 'Created', tone: 'neutral' },
  PENDING: { label: 'Pending', tone: 'info' },
  SUCCESS: { label: 'Success', tone: 'success' },
  REFUNDED: { label: 'Refunded', tone: 'neutral' },
}

const TONE_CLASSES = {
  neutral: 'bg-ink/5 text-ink-muted ring-ink/10',
  info: 'bg-blue-50 text-blue-700 ring-blue-200',
  success: 'bg-revive-50 text-revive-700 ring-revive-100',
  warning: 'bg-amber-50 text-amber-700 ring-amber-200',
  danger: 'bg-rose-50 text-rose-700 ring-rose-200',
}

export default function StatusBadge({ status }) {
  if (!status) return <span className="text-ink-muted">—</span>
  const config = STATUS_STYLES[status] || { label: status, tone: 'neutral' }
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium font-mono tracking-tight ring-1 ring-inset ${TONE_CLASSES[config.tone]}`}
    >
      <span className="h-1.5 w-1.5 rounded-full bg-current" />
      {config.label}
    </span>
  )
}
