import Icon from './Icon'
import Brand from './Brand'
import { brand } from '../../config/brand'

/** Split auth layout: brand story on the left (desktop), form card on the right. */
export default function AuthShell({ title, subtitle, children, footer }) {
  return (
    <div className="auth-page auth-split">
      <aside className="auth-aside" aria-hidden="true">
        <Brand />
        <div>
          <h2>{brand.signInHeadline}</h2>
          <p>{brand.signInBlurb}</p>
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
        <p className="small">
          © {new Date().getFullYear()} {brand.name}
        </p>
      </aside>

      <main className="auth-main">
        <div className="auth-card">
          <div className="auth-card-panel">
            <Brand center />
            <h1>{title}</h1>
            {subtitle && <p className="muted auth-subtitle">{subtitle}</p>}
            {children}
          </div>
          {footer && <div className="auth-footer">{footer}</div>}
        </div>
      </main>
    </div>
  )
}
