import { brand } from '../../config/brand'
import Brand from './Brand'

/**
 * Auth page shell: a single white card centred in the viewport on the brand
 * purple background. It is the only entry surface (employees at /login,
 * administrators at /admin/login), so the form itself is the focus.
 */
export default function AuthShell({ title, subtitle, children, footer }) {
  return (
    <div className="auth-page">
      <main className="auth-main">
        <div className="auth-card">
          <div className="auth-card-panel">
            <Brand center />
            <h1>{title}</h1>
            {subtitle && <p className="muted">{subtitle}</p>}
            {children}
          </div>
          <div className="auth-footer">
            {footer || (
              <span>
                © {new Date().getFullYear()} {brand.name}
              </span>
            )}
          </div>
        </div>
      </main>
    </div>
  )
}