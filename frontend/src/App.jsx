import { Navigate, Route, Routes } from 'react-router-dom'
import ProtectedRoute from './routes/ProtectedRoute'
import AppLayout from './components/layout/AppLayout'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import DashboardPage from './pages/DashboardPage'
import EmployeesPage from './pages/EmployeesPage'
import ClientsPage from './pages/ClientsPage'
import AttendancePage from './pages/AttendancePage'
import LeavesPage from './pages/LeavesPage'
import DailyReportsPage from './pages/DailyReportsPage'
import WeeklyReportsPage from './pages/WeeklyReportsPage'
import { FeedbackPage, FollowupsPage } from './pages/recordPages'
import PerformancePage from './pages/PerformancePage'
import ProfilePage from './pages/ProfilePage'
import { NotFoundPage, UnauthorizedPage } from './pages/StatusPages'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/unauthorized" element={<UnauthorizedPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/attendance" element={<AttendancePage />} />
          <Route path="/leaves" element={<LeavesPage />} />
          <Route path="/reports" element={<DailyReportsPage />} />
          <Route path="/weekly-reports" element={<WeeklyReportsPage />} />
          <Route path="/performance" element={<PerformancePage />} />
          <Route path="/feedback" element={<FeedbackPage />} />
          <Route path="/followups" element={<FollowupsPage />} />

          <Route element={<ProtectedRoute role="ADMIN" />}>
            <Route path="/employees" element={<EmployeesPage />} />
            <Route path="/clients" element={<ClientsPage />} />
          </Route>
          <Route element={<ProtectedRoute role="EMPLOYEE" />}>
            <Route path="/profile" element={<ProfilePage />} />
          </Route>

          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  )
}
