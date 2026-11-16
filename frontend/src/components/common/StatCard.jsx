import { Link } from 'react-router-dom'
import Icon from './Icon'

/**
 * Dashboard metric card. `to` makes it a navigable shortcut to the underlying page,
 * so every number on the dashboard leads to the data behind it.
 */
export default function StatCard({ label, value, hint, to, icon, tone }) {
  const body = (
    <>
      <div className="stat-top">
        <span className="stat-label">{label}</span>
        {icon && (
          <span className={`stat-icon${tone ? ` tone-${tone}` : ''}`}>
            <Icon name={icon} size={16} />
          </span>
        )}
      </div>
      <span className="stat-value">{value}</span>
      {hint && <span className="stat-hint">{hint}</span>}
    </>
  )

  return to ? (
    <Link to={to} className="card stat stat-link">
      {body}
    </Link>
  ) : (
    <div className="card stat">{body}</div>
  )
}
