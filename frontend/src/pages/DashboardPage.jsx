import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { adminDashboardApi } from '../api/dashboardApi'
import { employeePortalApi } from '../api/employeeApi'
import { employeeSessionApi } from '../api/attendanceApi'
import { getErrorMessage } from '../api/axiosClient'
import { useAuth } from '../hooks/useAuth'
import { useFetch } from '../hooks/useFetch'
import { useToast } from '../hooks/useToast'
import PageHeader from '../components/common/PageHeader'
import StatCard from '../components/common/StatCard'
import StatusBadge from '../components/common/StatusBadge'
import Skeleton from '../components/common/Skeleton'
import { EmptyState, ErrorState } from '../components/common/Feedback'
import Icon from '../components/common/Icon'
import EmployeeFormModal from '../components/employees/EmployeeFormModal'
import NotesPanel from '../components/common/NotesPanel'
import ReportingPanel from '../components/common/ReportingPanel'
import SourcingAnalytics from '../components/common/SourcingAnalytics'
import { notesApi } from '../api/notesApi'
import {
  formatDate,
  formatDuration,
  formatMoney,
  formatNumber,
  formatTimeIST,
  secondsSince,
  titleCase,
  todayISO,
} from '../utils/format'

/**
 * Today's attendance on the employee dashboard: the login state, the live working
 * time and the two actions. Attendance logout only closes the working session; it
 * does not sign the user out of the application.
 */
function DashboardAttendanceCard({ attendance, asOfDate, onChanged }) {
  const toast = useToast()
  const [busy, setBusy] = useState(null)
  const [live, setLive] = useState(() => Date.now())
  const [error, setError] = useState('')

  const open = Boolean(attendance.loggedIn)
  const activeSeconds = open ? secondsSince(attendance.activeSince, live) : 0
  const totalSeconds = (attendance.totalWorkSeconds ?? 0) + activeSeconds

  useEffect(() => {
    if (!open) return undefined
    const id = setInterval(() => setLive(Date.now()), 1000)
    return () => clearInterval(id)
  }, [open])

  async function act(action) {
    setBusy(action)
    setError('')
    try {
      await (action === 'login' ? employeeSessionApi.login() : employeeSessionApi.logout())
      toast.success(action === 'login' ? 'Logged in' : 'Logged out')
      onChanged()
    } catch (e) {
      const message = getErrorMessage(e)
      setError(message)
      toast.error(message)
    } finally {
      setBusy(null)
    }
  }

  return (
    <section className="card today-card">
      <div>
        <h2>Today · {formatDate(asOfDate)}</h2>
        <p className="muted">
          {open ? (
            <>
              You are <strong>logged in</strong> since {formatTimeIST(attendance.activeSince)} IST.
              {' '}Today&rsquo;s total: <strong>{formatDuration(totalSeconds)}</strong>
              {attendance.sessionCount > 1 && ` across ${attendance.sessionCount} sessions`}.
            </>
          ) : attendance.sessionCount > 0 ? (
            <>
              You are <strong>logged out</strong>. First login {formatTimeIST(attendance.firstLoginAt)},
              last logout {formatTimeIST(attendance.lastLogoutAt)} —{' '}
              <strong>{formatDuration(totalSeconds)}</strong> worked today.
            </>
          ) : attendance.onLeave ? (
            'You are on approved leave today.'
          ) : attendance.workingDay ? (
            'You haven’t logged in yet today.'
          ) : (
            `${titleCase(attendance.dayStatus)} — not a working day.`
          )}
        </p>
        {error && <p className="muted small">{error}</p>}
      </div>
      <div className="row-actions">
        {open ? (
          <button type="button" className="btn btn-danger" disabled={busy != null} onClick={() => act('logout')}>
            {busy === 'logout' ? 'Logging out…' : 'Log out'}
          </button>
        ) : (
          <button type="button" className="btn btn-success" disabled={busy != null} onClick={() => act('login')}>
            {busy === 'login' ? 'Logging in…' : 'Log in'}
          </button>
        )}
        <Link to="/attendance" className="btn btn-secondary">
          <Icon name="clock" size={16} /> Attendance
        </Link>
      </div>
    </section>
  )
}

export default function DashboardPage() {
  const navigate = useNavigate()
  const { user, isAdmin } = useAuth()
  const firstName = user.name?.split(' ')[0] ?? 'there'
  const [adding, setAdding] = useState(false)

  return (
    <>
      <PageHeader
        eyebrow={isAdmin ? 'Administrator' : 'Employee'}
        title={`Hello, ${firstName}`}
        subtitle={
          isAdmin
            ? 'Everything waiting on you, and how the team is doing today.'
            : 'Your activity, approvals and requests in one place.'
        }
        actions={
          isAdmin ? (
            <button type="button" className="btn btn-primary" onClick={() => setAdding(true)}>
              <Icon name="plus" size={16} /> Add employee
            </button>
          ) : null
        }
      />
      {isAdmin ? <AdminDashboard onAddReport={() => navigate('/reports?new=1')} /> : <EmployeeDashboard />}
      {adding && <AddEmployeeDialog onClose={() => setAdding(false)} />}
    </>
  )
}

/** Thin wrapper so the dashboard does not need to reload when an employee is added. */
function AddEmployeeDialog({ onClose }) {
  const toast = useToast()
  return (
    <EmployeeFormModal
      onClose={onClose}
      onSaved={(created) => toast.success(`${created.employeeName} added — they can sign in now`)}
    />
  )
}

/* ------------------------------- Admin ------------------------------- */

function AdminDashboard({ onAddReport }) {
  const { data, loading, error, reload } = useFetch(adminDashboardApi.summary)

  if (loading) {
    return (
      <>
        <div className="stats">
          {Array.from({ length: 4 }).map((_, i) => (
            <div className="card" key={i}>
              <Skeleton rows={2} />
            </div>
          ))}
        </div>
        <div className="grid-2">
          <div className="card">
            <Skeleton rows={4} />
          </div>
          <div className="card">
            <Skeleton rows={4} />
          </div>
        </div>
      </>
    )
  }
  if (error) return <ErrorState message={error} onRetry={reload} />

  const { people, attendanceToday, leaves, monthlyPerformance, recentEmployees } = data

  return (
    <>
      {/* Top line: the four numbers an administrator checks first. */}
      <div className="stats">
        <StatCard
          label="Active employees"
          value={formatNumber(people.activeEmployees)}
          hint={`${people.totalEmployees} total · ${people.pendingEmployees} pending`}
          icon="users"
          to="/employees"
        />
        <StatCard
          label="Present today"
          value={formatNumber(attendanceToday.presentToday)}
          hint={`${attendanceToday.presencePercent}% of active team · ${attendanceToday.notMarkedToday} not marked`}
          icon="clock"
          tone="green"
          to="/attendance"
        />
        <StatCard
          label="Leave awaiting review"
          value={formatNumber(leaves.pending)}
          hint={`${leaves.requestsThisMonth} requests this month`}
          icon="calendar"
          tone={leaves.pending > 0 ? 'amber' : undefined}
          to="/leaves"
        />
        <StatCard
          label="Revenue this month"
          value={formatMoney(monthlyPerformance.totalRevenue)}
          hint={`${monthlyPerformance.weeksCounted} weekly reports · ${monthlyPerformance.employeesWithRevenue} earning`}
          icon="chart"
          to="/performance-tracker"
        />
      </div>

      {/* Sourcing analytics: counted from daily reports, range first report -> today. */}
      <SourcingAnalytics onAddNew={onAddReport} />

      <div className="grid-2">
        <NotesPanel
          api={notesApi.admin}
          title="Notepad & thoughts"
          subtitle="One notepad for everything you want to remember. Saved to your administrator account."
          placeholder="Decisions, reminders, candidates to follow up…"
          id="admin-notepad"
          limit={5}
        />

        <section className="card">
          <div className="card-header">
            <h2>Newest team members</h2>
            <Link to="/employees" className="small">
              Manage team →
            </Link>
          </div>
          {recentEmployees.length === 0 ? (
            <EmptyState
              icon="users"
              title="No employees yet"
              hint="Add your first employee — you set their employee ID, email, phone, department, role, salary and password."
            />
          ) : (
            <ul className="list">
              {recentEmployees.map((e) => (
                <li key={e.employeeId}>
                  <div style={{ minWidth: 0 }}>
                    <strong>{e.employeeName}</strong>
                    <div className="muted small">
                      {e.employeeCode || `#${e.employeeId}`} · {e.department || e.role}
                    </div>
                  </div>
                  <StatusBadge value={e.accountStatus} />
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <ReportingPanel />
    </>
  )
}

/* ------------------------------ Employee ------------------------------ */

function EmployeeDashboard() {
  const { data, loading, error, reload } = useFetch(employeePortalApi.dashboard)

  if (loading) {
    return (
      <div className="stats">
        {Array.from({ length: 4 }).map((_, i) => (
          <div className="card" key={i}>
            <Skeleton rows={2} />
          </div>
        ))}
      </div>
    )
  }
  if (error) return <ErrorState message={error} onRetry={reload} />

  const { attendance, leaves, dailyReports, weeklyReports } = data

  return (
    <>
      <DashboardAttendanceCard attendance={attendance} asOfDate={data.asOfDate ?? todayISO()} onChanged={reload} />

      <div className="stats">
        <StatCard
          label="Present this month"
          value={attendance.presentThisMonth}
          hint={`${attendance.absentThisMonth} absent · ${attendance.recordedThisMonth} recorded`}
          icon="clock"
          tone="green"
          to="/attendance"
        />
        <StatCard
          label="Pending leave"
          value={leaves.pendingRequests}
          hint={`${formatMoney(leaves.totalApprovedDays)} days approved`}
          icon="calendar"
          tone={leaves.pendingRequests > 0 ? 'amber' : undefined}
          to="/leaves"
        />
        <StatCard
          label="Daily reports"
          value={dailyReports.reportCount}
          hint={`${formatNumber(dailyReports.profilesSubmitted)} profiles submitted`}
          icon="file"
          to="/reports"
        />
        <StatCard
          label="Weekly reports"
          value={weeklyReports.reportCount}
          hint={`${formatNumber(weeklyReports.joined)} joined`}
          icon="chart"
          to="/weekly-reports"
        />
      </div>

      <div className="grid-2">
        <section className="card">
          <div className="card-header">
            <h2>Daily report totals</h2>
            <Link to="/reports" className="small">
              Open →
            </Link>
          </div>
          <dl className="details">
            <div>
              <dt>Sourced</dt>
              <dd>{formatNumber(dailyReports.candidatesSourced)}</dd>
            </div>
            <div>
              <dt>Contacted</dt>
              <dd>{formatNumber(dailyReports.candidatesContacted)}</dd>
            </div>
            <div>
              <dt>Connected</dt>
              <dd>{formatNumber(dailyReports.connectedCount)}</dd>
            </div>
            <div>
              <dt>Profiles submitted</dt>
              <dd>{formatNumber(dailyReports.profilesSubmitted)}</dd>
            </div>
          </dl>
        </section>
        <section className="card">
          <div className="card-header">
            <h2>Weekly report totals</h2>
            <Link to="/weekly-reports" className="small">
              Open →
            </Link>
          </div>
          <dl className="details">
            <div>
              <dt>Interviews scheduled</dt>
              <dd>{formatNumber(weeklyReports.interviewsScheduled)}</dd>
            </div>
            <div>
              <dt>Final selections</dt>
              <dd>{formatNumber(weeklyReports.finalSelections)}</dd>
            </div>
            <div>
              <dt>Joined</dt>
              <dd>{formatNumber(weeklyReports.joined)}</dd>
            </div>
            <div>
              <dt>Revenue generated</dt>
              <dd>{formatMoney(weeklyReports.revenueGenerated)}</dd>
            </div>
          </dl>
        </section>
      </div>

      <section className="card">
        <h2>Leave so far</h2>
        <dl className="details">
          <div>
            <dt>Approved</dt>
            <dd>{leaves.approvedRequests}</dd>
          </div>
          <div>
            <dt>Rejected</dt>
            <dd>{leaves.rejectedRequests}</dd>
          </div>
          <div>
            <dt>Cancelled</dt>
            <dd>{leaves.cancelledRequests}</dd>
          </div>
          <div>
            <dt>Approved days</dt>
            <dd>{formatMoney(leaves.totalApprovedDays)}</dd>
          </div>
        </dl>
      </section>

      <ReportingPanel />
    </>
  )
}
