import { Routes, Route } from 'react-router-dom'
import Dashboard from './pages/Dashboard.jsx'
import RecoveryCases from './pages/RecoveryCases.jsx'
import CaseDetail from './pages/CaseDetail.jsx'
import Customers from './pages/Customers.jsx'
import Payments from './pages/Payments.jsx'
import AgentActivity from './pages/AgentActivity.jsx'
import Settings from './pages/Settings.jsx'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Dashboard />} />
      <Route path="/recovery-cases" element={<RecoveryCases />} />
      <Route path="/recovery-cases/:id" element={<CaseDetail />} />
      <Route path="/customers" element={<Customers />} />
      <Route path="/payments" element={<Payments />} />
      <Route path="/agent-activity" element={<AgentActivity />} />
      <Route path="/settings" element={<Settings />} />
    </Routes>
  )
}
