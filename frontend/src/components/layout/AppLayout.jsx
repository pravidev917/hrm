import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import { ADMIN_NAV, EMPLOYEE_NAV, navFor } from './navItems'
import Icon from '../common/Icon'
import Brand from '../common/Brand'
import { brand } from '../../config/brand'
import { useNavBadges } from '../../hooks/useNavBadges'

export default function AppLayout() {
  const { user, isAdmin, logout } = useAuth()
  const location = useLocation()
  const [open, setOpen] = useState(false)
  const nav = isAdmin ? ADMIN_NAV : EMPLOYEE_NAV
  const badges = useNavBadges(isAdmin)

  // Close the mobile drawer on navigation (state is reset from the click handlers below)
  // and on Escape. Escape is an external event subscription, so an effect is appropriate.
  // While the drawer is open the page behind it must not scroll, so only the menu moves.
  useEffect(() => {
    if (!open) return
    const onKey = (e) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('keydown', onKey)
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = previousOverflow
    }
  }, [open])

  const current = navFor(isAdmin).find((item) => item.to === location.pathname)
  const initials = (user?.name || user?.email || '?').trim().charAt(0).toUpperCase()

  return (
    <div className="shell">
      <a href="#main" className="skip-link">
        Skip to content
      </a>

      <aside
        id="primary-navigation"
        className={open ? 'sidebar open sidebar-open-scroll' : 'sidebar'}
        aria-label="Primary navigation"
      >
        <Brand subtitle={isAdmin ? brand.subtitleAdmin : brand.subtitleEmployee} />

        <nav aria-label="Sections">
          {nav.map((group) => (
            <div key={group.section}>
              <span className="nav-section">{group.section}</span>
              {group.items.map((item) => {
                const count = item.badgeKey ? badges[item.badgeKey] : 0
                return (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    onClick={() => setOpen(false)}
                    className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
                  >
                    <Icon name={item.icon} />
                    <span>{item.label}</span>
                    {count > 0 && (
                      <span className="nav-badge" aria-label={`${count} pending`}>
                        {count > 99 ? '99+' : count}
                      </span>
                    )}
                  </NavLink>
                )
              })}
            </div>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className="sidebar-user">
            <span className="avatar avatar-dark" aria-hidden="true">
              {initials}
            </span>
            <div style={{ minWidth: 0 }}>
              <strong title={user?.name}>{user?.name}</strong>
              <span className="small">{isAdmin ? 'Administrator' : 'Employee'}</span>
            </div>
          </div>
        </div>
      </aside>

      {open && <div className="scrim" onClick={() => setOpen(false)} aria-hidden="true" />}

      <div className="content">
        <header className="topbar">
          <button
            type="button"
            className="icon-btn menu-btn"
            aria-label={open ? 'Close navigation' : 'Open navigation'}
            aria-expanded={open}
            aria-controls="primary-navigation"
            onClick={() => setOpen((o) => !o)}
          >
            <Icon name={open ? 'close' : 'menu'} size={22} />
          </button>

          <span className="topbar-title">{current?.label ?? brand.name}</span>
          <div className="spacer" />

          <div className="user-chip">
            <span className="avatar" aria-hidden="true">
              {initials}
            </span>
            <span className="user-meta">
              <strong>{user.name}</strong>
              <span className="muted small">{user.email}</span>
            </span>
          </div>
          <button type="button" className="btn btn-sm btn-ghost" onClick={logout} title="Sign out">
            <Icon name="logout" size={16} />
            <span className="sign-out-label">Sign out</span>
          </button>
        </header>

        <main id="main" className="main" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}
