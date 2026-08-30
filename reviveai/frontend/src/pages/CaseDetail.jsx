import { useParams, Link } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import Layout from '../components/Layout.jsx'

export default function CaseDetail() {
  const { id } = useParams()
  return (
    <Layout title="Recovery Case" subtitle={id}>
      <Link to="/recovery-cases" className="mb-4 inline-flex items-center gap-1.5 text-sm text-ink-muted hover:text-ink">
        <ArrowLeft className="h-3.5 w-3.5" /> Back to Recovery Cases
      </Link>
      <div className="rounded-card border border-dashed border-line bg-white/50 py-16 text-center text-sm text-ink-muted">
        Full case detail (AI reasoning, Policy UI, timeline) is coming in the next build phase.
      </div>
    </Layout>
  )
}
