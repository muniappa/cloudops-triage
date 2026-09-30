import { NavLink, Outlet } from 'react-router-dom'
import clsx from 'clsx'

const navItems = [
  { to: '/',         label: 'Incidents',    icon: '🔔' },
  { to: '/services', label: 'Services',     icon: '🖧'  },
]

export default function AppShell() {
  return (
    <div className="flex h-screen overflow-hidden bg-surface">
      {/* ── Sidebar ──────────────────────────────────────────────────── */}
      <aside className="w-52 shrink-0 flex flex-col border-r border-border bg-white">
        {/* Logo / wordmark */}
        <div className="px-4 py-4 border-b border-border">
          <div className="flex items-center gap-2">
            <span className="text-lg">⚡</span>
            <div>
              <p className="font-semibold text-sm leading-tight">CloudOps</p>
              <p className="text-xs text-gray-400 leading-tight">Triage Console</p>
            </div>
          </div>
        </div>

        {/* Nav */}
        <nav className="flex-1 px-2 py-3 space-y-0.5">
          {navItems.map(item => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                clsx(
                  'flex items-center gap-2.5 rounded-md px-3 py-2 text-sm transition-colors',
                  isActive
                    ? 'bg-accent/10 text-accent font-medium'
                    : 'text-gray-600 hover:bg-surface hover:text-gray-900'
                )
              }
            >
              <span>{item.icon}</span>
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        {/* Footer */}
        <div className="px-4 py-3 border-t border-border">
          <p className="text-xs text-gray-400">Polling every 10 s</p>
        </div>
      </aside>

      {/* ── Main content ─────────────────────────────────────────────── */}
      <main className="flex-1 overflow-y-auto">
        <div className="mx-auto max-w-5xl px-6 py-6">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
