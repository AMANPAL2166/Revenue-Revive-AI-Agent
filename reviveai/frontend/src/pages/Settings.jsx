import Layout from '../components/Layout.jsx'

export default function Settings() {
  return (
    <Layout title="Settings" subtitle="Merchant policy limits and demo configuration.">
      <div className="rounded-card border border-dashed border-line bg-white/50 py-20 text-center text-sm text-ink-muted">
        Settings UI isn't built yet — policy limits currently live in the backend's application.yml.
      </div>
    </Layout>
  )
}
