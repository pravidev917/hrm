import Icon from './Icon'

/** Inline spinner with an accessible label. */
export function Spinner({ label = 'Loading…', fullPage = false, large = false }) {
  return (
    <div className={fullPage ? 'state state-full' : 'state'} role="status" aria-live="polite">
      <span className={large ? 'spinner spinner-lg' : 'spinner'} aria-hidden="true" />
      <span className="muted">{label}</span>
    </div>
  )
}

/**
 * Honest failure state: says the request failed and offers a retry.
 * Never renders placeholder data in place of a failed response.
 */
export function ErrorState({ message, onRetry, title = 'We couldn’t load this' }) {
  return (
    <div className="state state-error" role="alert">
      <span className="state-icon">
        <Icon name="alert" size={22} />
      </span>
      <span className="state-title">{title}</span>
      <span className="muted">{message}</span>
      {onRetry && (
        <button type="button" className="btn btn-secondary" onClick={onRetry} style={{ marginTop: 8 }}>
          <Icon name="arrow-right" size={16} /> Try again
        </button>
      )}
    </div>
  )
}

export function EmptyState({ title, hint, action, icon = 'inbox' }) {
  return (
    <div className="state">
      <span className="state-icon">
        <Icon name={icon} size={22} />
      </span>
      <span className="state-title">{title}</span>
      {hint && <span className="muted">{hint}</span>}
      {action && <div style={{ marginTop: 8 }}>{action}</div>}
    </div>
  )
}

export function Alert({ type = 'error', children }) {
  const icon = type === 'success' ? 'check-circle' : type === 'info' ? 'info' : 'alert'
  return (
    <div className={`alert alert-${type}`} role={type === 'error' ? 'alert' : 'status'}>
      <Icon name={icon} size={16} />
      <span>{children}</span>
    </div>
  )
}
