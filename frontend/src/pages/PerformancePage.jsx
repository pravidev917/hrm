import { Navigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import EmployeePerformancePage from './EmployeePerformancePage'

/**
 * `/performance` is the employee view. Administrators get the admin tracker
 * (leaderboard + revenue report) on its own route instead of a personal target
 * page that would have no meaning for them.
 */
export default function PerformancePage() {
  const { isAdmin } = useAuth()
  if (isAdmin) return <Navigate to="/performance-tracker" replace />
  return <EmployeePerformancePage />
}
