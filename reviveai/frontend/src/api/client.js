const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

async function request(path, options = {}) {
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  })

  if (!res.ok) {
    let message = `Request failed (${res.status})`
    try {
      const body = await res.json()
      message = body.message || message
    } catch {
      // response wasn't JSON — keep the generic message
    }
    throw new Error(message)
  }

  if (res.status === 204) return null
  return res.json()
}

function toQuery(params) {
  const entries = Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== '')
  const query = new URLSearchParams(entries).toString()
  return query ? `?${query}` : ''
}

export const api = {
  getDashboardSummary: () => request('/dashboard/summary'),
  getRevenueBreakdown: () => request('/dashboard/revenue-risk'),
  getAgentActivity: (page = 0, size = 10) => request(`/dashboard/agent-activity${toQuery({ page, size })}`),

  getRecoveryCases: (params = {}) => request(`/recovery-cases${toQuery(params)}`),
  getRecoveryCase: (id) => request(`/recovery-cases/${id}`),
  analyzeCase: (id) => request(`/recovery-cases/${id}/analyze`, { method: 'POST' }),
  executeCase: (id) => request(`/recovery-cases/${id}/execute`, { method: 'POST' }),
  approveCase: (id) => request(`/recovery-cases/${id}/approve`, { method: 'POST' }),
  rejectCase: (id) => request(`/recovery-cases/${id}/reject`, { method: 'POST' }),

  getPayments: (page = 0, size = 20) => request(`/payments${toQuery({ page, size })}`),
  getCustomer: (id) => request(`/customers/${id}`),
}
