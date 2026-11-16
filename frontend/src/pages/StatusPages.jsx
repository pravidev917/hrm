import { Link } from 'react-router-dom'
import Icon from '../components/common/Icon'

function StatusPage({ code, title, message, action }) {
  return (
    <div className="state state-full">
      <span className="state-icon">
        <Icon name="info" size={22} />
      </span>
      <span className="big-code">{code}</span>
      <span className="state-title">{title}</span>
      <span className="muted">{message}</span>
      <div style={{ marginTop: 12 }}>{action}</div>
    </div>
  )
}

export const NotFoundPage = () => (
  <StatusPage
    code="404"
    title="Page not found"
    message="The page you are looking for doesn’t exist or has moved."
    action={
      <Link to="/dashboard" className="btn btn-primary">
        Back to dashboard
      </Link>
    }
  />
)

export const UnauthorizedPage = () => (
  <StatusPage
    code="403"
    title="No access"
    message="Your account doesn’t have permission to view this page."
    action={
      <Link to="/dashboard" className="btn btn-primary">
        Back to dashboard
      </Link>
    }
  />
)
