import { Navigate, Route, Routes } from 'react-router-dom'
import ProtectedRoute from './routes/ProtectedRoute'
import AppLayout from './components/layout/AppLayout'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import EmployeesPage from './pages/EmployeesPage'
import AdministratorsPage from './pages/AdministratorsPage'
import ClientsPage from './pages/ClientsPage'
import AttendancePage from './pages/AttendancePage'
import LeavesPage from './pages/LeavesPage'
import WfhPage from './pages/WfhPage'
import OfficeSettingsPage from './pages/OfficeSettingsPage'
import DailyReportsPage from './pages/DailyReportsPage'
import WeeklyReportsPage from './pages/WeeklyReportsPage'
import { FeedbackPage, FollowupsPage } from './pages/recordPages'
import PerformancePage from './pages/PerformancePage'
import PerformanceTrackerPage from './pages/PerformanceTrackerPage'
import ProfilePage from './pages/ProfilePage'
import { NotFoundPage, UnauthorizedPage } from './pages/StatusPages'

export default function App() {
  return (
    <Routes>
      // Two separate entry points, each with its own URL and endpoint.
      <Route path="/login" element={<LoginPage role="EMPLOYEE" />} />
      <Route path="/admin/login" element={<LoginPage role="ADMIN" />} />
      <Route path="/unauthorized" element={<UnauthorizedPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/attendance" element={<AttendancePage />} />
          <Route path="/leaves" element={<LeavesPage />} />
          <Route path="/wfh" element={<WfhPage />} />
          <Route path="/reports" element={<DailyReportsPage />} />
          <Route path="/weekly-reports" element={<WeeklyReportsPage />} />
          <Route path="/performance" element={<PerformancePage />} />
          <Route path="/candidate-tracking" element={<FeedbackPage />} />
          <Route path="/feedback" element={<Navigate to="/candidate-tracking" replace />} />
          <Route element={<ProtectedRoute role="EMPLOYEE" />}>
            <Route path="/followups" element={<FollowupsPage />} />
          </Route>

          <Route element={<ProtectedRoute role="ADMIN" />}>
            <Route path="/employees" element={<EmployeesPage />} />
            <Route path="/administrators" element={<AdministratorsPage />} />
            <Route path="/clients" element={<ClientsPage />} />
            <Route path="/office-settings" element={<OfficeSettingsPage />} />
            <Route path="/performance-tracker" element={<PerformanceTrackerPage />} />
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
