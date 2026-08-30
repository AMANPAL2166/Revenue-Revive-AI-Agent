import Sidebar from './Sidebar.jsx'

export default function Layout({ children, title, subtitle, actions }) {
  return (
    <div className="min-h-screen bg-paper">
      <Sidebar />
      <div className="pl-60">
        <header className="sticky top-0 z-10 flex items-center justify-between border-b border-line bg-paper/90 px-8 py-5 backdrop-blur">
          <div>
            <h1 className="font-display text-xl font-semibold text-ink">{title}</h1>
            {subtitle && <p className="mt-0.5 text-sm text-ink-muted">{subtitle}</p>}
          </div>
          {actions}
        </header>
        <main className="px-8 py-6">{children}</main>
      </div>
    </div>
  )
}
