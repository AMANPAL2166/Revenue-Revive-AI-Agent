const currencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
})

export function formatCurrency(value) {
  if (value === null || value === undefined) return '—'
  return currencyFormatter.format(value)
}

export function formatPercent(value, { fromFraction = false } = {}) {
  if (value === null || value === undefined) return '—'
  const pct = fromFraction ? value * 100 : value
  return `${pct.toFixed(1)}%`
}

export function formatDateTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString('en-IN', {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatRelative(value) {
  if (!value) return '—'
  const diffMs = Date.now() - new Date(value).getTime()
  const mins = Math.round(diffMs / 60000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins}m ago`
  const hours = Math.round(mins / 60)
  if (hours < 24) return `${hours}h ago`
  return `${Math.round(hours / 24)}d ago`
}

/** RETRY_PAYMENT -> "Retry Payment" */
export function formatActionLabel(action) {
  if (!action) return '—'
  return action
    .replace(/_/g, ' ')
    .toLowerCase()
    .replace(/\b\w/g, (c) => c.toUpperCase())
}
