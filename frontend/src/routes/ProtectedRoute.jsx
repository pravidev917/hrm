import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { Spinner } from '../components/common/Feedback'

/** Requires a signed-in user; optionally restricts to a role. Backend remains the real authority. */
export default function ProtectedRoute({ role }) {
  const { user, initializing } = useAuth()
  const location = useLocation()

  if (initializing) return <Spinner label="Checking your session…" fullPage />
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (role && user.role !== role) return <Navigate to="/unauthorized" replace />
  return <Outlet />
}
