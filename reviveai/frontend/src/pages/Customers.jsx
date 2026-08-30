import { Users } from 'lucide-react'
import Layout from '../components/Layout.jsx'

export default function Customers() {
  return (
    <Layout title="Customers" subtitle="Customer profiles and payment history.">
      <div className="flex flex-col items-center justify-center rounded-card border border-dashed border-line bg-white/50 py-20 text-center">
        <Users className="h-8 w-8 text-ink-muted" strokeWidth={1.5} />
        <p className="mt-3 text-sm font-medium text-ink">Customer directory isn't built yet</p>
        <p className="mt-1 max-w-sm text-sm text-ink-muted">
          The backend only exposes a single-customer lookup today (GET /api/customers/&#123;id&#125;). A
          searchable list view is planned for a later phase.
        </p>
      </div>
    </Layout>
  )
}
