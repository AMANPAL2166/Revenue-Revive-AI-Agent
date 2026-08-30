import { NavLink } from 'react-router-dom'
import { LayoutDashboard, ListChecks, Users, CreditCard, Activity, Settings } from 'lucide-react'

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/recovery-cases', label: 'Recovery Cases', icon: ListChecks },
  { to: '/customers', label: 'Customers', icon: Users },
  { to: '/payments', label: 'Payments', icon: CreditCard },
  { to: '/agent-activity', label: 'Agent Activity', icon: Activity },
  { to: '/settings', label: 'Settings', icon: Settings },
]

export default function Sidebar() {
  return (
    <aside className="fixed inset-y-0 left-0 flex w-60 shrink-0 flex-col bg-ink text-white/80">
      <div className="flex items-center gap-2 px-5 py-6">
        <div className="flex h-7 w-7 items-center justify-center rounded bg-revive-600 font-display text-sm font-bold text-white">
          R
        </div>
        <div>
          <div className="font-display text-sm font-semibold leading-none text-white">ReviveAI</div>
          <div className="mt-0.5 text-[10px] tracking-wide text-white/40">Revenue Recovery</div>
        </div>
      </div>

      <nav className="flex-1 space-y-0.5 px-3">
        {NAV_ITEMS.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors ${
                isActive ? 'bg-white/10 text-white' : 'text-white/60 hover:bg-white/5 hover:text-white/90'
              }`
            }
          >
            <Icon className="h-4 w-4" strokeWidth={1.75} />
            {label}
          </NavLink>
        ))}
      </nav>

      <div className="border-t border-white/10 px-5 py-4 font-mono text-[11px] leading-relaxed text-white/30">
        AI recommends. Policy controls.
        <br />
        Backend executes. Metrics measure.
      </div>
    </aside>
  )
}
