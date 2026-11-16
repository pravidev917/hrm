import Icon from './Icon'

/** Split auth layout: brand story on the left (desktop), form card on the right. */
export default function AuthShell({ title, subtitle, children, footer }) {
  return (
    <div className="auth-page auth-split">
      <aside className="auth-aside" aria-hidden="true">
        <div className="brand">
          <span className="brand-mark">H</span>
          <span>HRM</span>
        </div>
        <div>
          <h2>People operations in one calm place.</h2>
          <p>Attendance, leave, reports and performance for your whole team — without spreadsheets.</p>
          <ul className="auth-points">
            <li>
              <Icon name="calendar" size={16} /> Request and approve leave with automatic working-day counts
            </li>
            <li>
              <Icon name="file" size={16} /> Daily and weekly recruitment reporting with live totals
            </li>
            <li>
              <Icon name="shield" size={16} /> Role-based access: administrators and employees see only their own data
            </li>
          </ul>
        </div>
        <p className="small">© {new Date().getFullYear()} HRM</p>
      </aside>

      <main className="auth-main">
        <div className="auth-card">
          <div className="auth-card-panel">
            <div className="brand brand-center">
              <span className="brand-mark">H</span>
              <span>HRM</span>
            </div>
            <h1>{title}</h1>
            {subtitle && <p className="muted">{subtitle}</p>}
            {children}
          </div>
          {footer && <div className="auth-footer">{footer}</div>}
        </div>
      </main>
    </div>
  )
}
