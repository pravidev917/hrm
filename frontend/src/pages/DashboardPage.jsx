import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { adminDashboardApi } from '../api/dashboardApi'
import { employeePortalApi } from '../api/employeeApi'
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
import { formatDate, formatMoney, formatNumber, todayISO } from '../utils/format'

function monthLabel(month) {
  if (!month) return '—'
  const [year, index] = month.split('-').map(Number)
  return new Intl.DateTimeFormat(undefined, { month: 'long', year: 'numeric' }).format(new Date(year, index - 1, 1))
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
      {isAdmin ? (
        <AdminDashboard onAddEmployee={() => setAdding(true)} onAddReport={() => navigate('/reports?new=1')} />
      ) : (
        <EmployeeDashboard />
      )}
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

function AdminDashboard({ onAddEmployee, onAddReport }) {
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

  const { people, attendanceToday, leaves, dailyReports, monthlyPerformance, pendingLeaveQueue, recentEmployees } = data

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

      {/* Detail cards. */}
      <div className="grid-2">
        <section className="card">
          <div className="card-header">
            <div>
              <h2>Leave details</h2>
              <p className="muted small">All requests, plus this month&apos;s decisions.</p>
            </div>
            <Link to="/leaves" className="small">
              Open →
            </Link>
          </div>
          <div className="mini-grid">
            <div className="mini-stat">
              <span className="mini-label">Total requests</span>
              <span className="mini-value">{formatNumber(leaves.totalRequests)}</span>
            </div>
            <div className="mini-stat">
              <span className="mini-label">Pending</span>
              <span className={`mini-value${leaves.pending > 0 ? ' tone-warn' : ''}`}>{formatNumber(leaves.pending)}</span>
            </div>
            <div className="mini-stat">
              <span className="mini-label">Approved</span>
              <span className="mini-value tone-success">{formatNumber(leaves.approvedRequests)}</span>
            </div>
            <div className="mini-stat">
              <span className="mini-label">Rejected</span>
              <span className="mini-value tone-danger">{formatNumber(leaves.rejectedRequests)}</span>
            </div>
          </div>
          <p className="muted small">
            This month: {leaves.approvedThisMonth} approved · {leaves.rejectedThisMonth} rejected ·{' '}
            {formatMoney(leaves.approvedDaysThisMonth)} approved days
          </p>
        </section>

        <section className="card">
          <div className="card-header">
            <div>
              <h2>Attendance details</h2>
              <p className="muted small">{formatDate(data.asOfDate)}</p>
            </div>
            <Link to="/attendance" className="small">
              Open →
            </Link>
          </div>
          <div className="mini-grid">
            <div className="mini-stat">
              <span className="mini-label">Present</span>
              <span className="mini-value tone-success">{formatNumber(attendanceToday.presentToday)}</span>
            </div>
            <div className="mini-stat">
              <span className="mini-label">Absent</span>
              <span className="mini-value tone-danger">{formatNumber(attendanceToday.absentToday)}</span>
            </div>
            <div className="mini-stat">
              <span className="mini-label">Not marked</span>
              <span className="mini-value">{formatNumber(attendanceToday.notMarkedToday)}</span>
            </div>
            <div className="mini-stat">
              <span className="mini-label">Late</span>
              <span className="mini-value muted small">
                {attendanceToday.lateTracked ? formatNumber(attendanceToday.lateToday ?? 0) : 'Not tracked'}
              </span>
            </div>
          </div>
          <div className="progress" role="img" aria-label={`${attendanceToday.presencePercent}% of the team is present`}>
            <span className="progress-bar" style={{ width: `${Math.min(100, attendanceToday.presencePercent)}%` }} />
          </div>
          <p className="muted small">{attendanceToday.presencePercent}% of active employees marked present.</p>
        </section>

        <section className="card">
          <div className="card-header">
            <div>
              <h2>Daily reports</h2>
              <p className="muted small">{formatDate(dailyReports.date)}</p>
            </div>
            <Link to="/reports" className="small">
              Open →
            </Link>
          </div>
          <div className="mini-grid">
            <div className="mini-stat">
              <span className="mini-label">Submitted today</span>
              <span className="mini-value">{formatNumber(dailyReports.reportsToday)}</span>
            </div>
            <div className="mini-stat">
              <span className="mini-label">Employees reporting</span>
              <span className="mini-value">
                {dailyReports.employeesSubmittedToday}
                <span className="muted small"> / {people.activeEmployees}</span>
              </span>
            </div>
            <div className="mini-stat">
              <span className="mini-label">Missing</span>
              <span className={`mini-value${dailyReports.employeesMissingToday > 0 ? ' tone-warn' : ''}`}>
                {formatNumber(dailyReports.employeesMissingToday)}
              </span>
            </div>
            <div className="mini-stat">
              <span className="mini-label">Resumes today</span>
              <span className="mini-value tone-success">{formatNumber(dailyReports.resumesToday)}</span>
            </div>
          </div>
          <p className="muted small">
            This month: {formatNumber(dailyReports.reportsThisMonth)} reports ·{' '}
            {formatNumber(dailyReports.resumesThisMonth)} resumes
          </p>
        </section>

        <section className="card">
          <div className="card-header">
            <div>
              <h2>Monthly performance</h2>
              <p className="muted small">
                {monthLabel(monthlyPerformance.month)} · weekly revenue summed per employee
              </p>
            </div>
            <Link to="/performance-tracker" className="small">
              Full report →
            </Link>
          </div>
          <div className="mini-grid">
            <div className="mini-stat">
              <span className="mini-label">Total revenue</span>
              <span className="mini-value tone-success">{formatMoney(monthlyPerformance.totalRevenue)}</span>
            </div>
            <div className="mini-stat">
              <span className="mini-label">Employees earning</span>
              <span className="mini-value">{formatNumber(monthlyPerformance.employeesWithRevenue)}</span>
            </div>
            <div className="mini-stat">
              <span className="mini-label">Top employee</span>
              <span className="mini-value small">{monthlyPerformance.topEmployeeName ?? '—'}</span>
            </div>
            <div className="mini-stat">
              <span className="mini-label">Top revenue</span>
              <span className="mini-value small">{formatMoney(monthlyPerformance.topEmployeeRevenue)}</span>
            </div>
          </div>
          {monthlyPerformance.rows.length === 0 ? (
            <p className="muted small">No weekly revenue recorded for this month yet.</p>
          ) : (
            <ul className="list compact">
              {[...monthlyPerformance.rows]
                .sort((a, b) => (Number(b.revenue) || 0) - (Number(a.revenue) || 0))
                .slice(0, 4)
                .map((row) => (
                  <li key={row.employeeId}>
                    <div style={{ minWidth: 0 }}>
                      <strong>{row.employeeName}</strong>
                      <div className="muted small">
                        {row.weeksCounted} week{row.weeksCounted === 1 ? '' : 's'}
                      </div>
                    </div>
                    <span>{formatMoney(row.revenue)}</span>
                  </li>
                ))}
            </ul>
          )}
        </section>
      </div>

      <div className="grid-2">
        <section className="card">
          <div className="card-header">
            <h2>Review queue</h2>
            <Link to="/leaves" className="small">
              All requests →
            </Link>
          </div>
          {pendingLeaveQueue.length === 0 ? (
            <EmptyState icon="check-circle" title="Nothing to review" hint="No leave requests are waiting for your decision." />
          ) : (
            <ul className="list">
              {pendingLeaveQueue.map((l) => (
                <li key={l.leaveId}>
                  <div style={{ minWidth: 0 }}>
                    <strong>{l.employeeName}</strong>
                    <div className="muted small">
                      {formatDate(l.startDate)}
                      {l.endDate !== l.startDate && ` – ${formatDate(l.endDate)}`} · {l.numberOfDays} day
                      {Number(l.numberOfDays) === 1 ? '' : 's'} · {l.type}
                    </div>
                  </div>
                  <Link to="/leaves" className="btn btn-sm btn-primary">
                    Review
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="card">
          <div className="card-header">
            <h2>Quick actions</h2>
          </div>
          <div className="quick-actions">
            <button type="button" className="btn btn-block" onClick={onAddEmployee}>
              <Icon name="plus" size={16} /> Quick add employee
            </button>
            <Link to="/clients?new=1" className="btn btn-block">
              <Icon name="briefcase" size={16} /> Quick add client
            </Link>
            <Link to="/leaves" className="btn btn-block">
              <Icon name="calendar" size={16} /> Review leave
            </Link>
            <Link to="/reports" className="btn btn-block">
              <Icon name="file" size={16} /> Team daily reports
            </Link>
            <Link to="/weekly-reports" className="btn btn-block">
              <Icon name="chart" size={16} /> Weekly reports
            </Link>
          </div>
        </section>
      </div>

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

      <div className="grid-3">
        <section className="card">
          <h2>This month</h2>
          <dl className="details">
            <div>
              <dt>Leave approved</dt>
              <dd>
                {leaves.approvedThisMonth} · {formatMoney(leaves.approvedDaysThisMonth)} days
              </dd>
            </div>
            <div>
              <dt>Leave rejected</dt>
              <dd>{leaves.rejectedThisMonth}</dd>
            </div>
            <div>
              <dt>Requests started</dt>
              <dd>{leaves.requestsThisMonth}</dd>
            </div>
          </dl>
        </section>
        <section className="card">
          <h2>Attendance today</h2>
          <dl className="details">
            <div>
              <dt>Present</dt>
              <dd>{attendanceToday.presentToday}</dd>
            </div>
            <div>
              <dt>Absent</dt>
              <dd>{attendanceToday.absentToday}</dd>
            </div>
            <div>
              <dt>Not marked</dt>
              <dd>{attendanceToday.notMarkedToday}</dd>
            </div>
          </dl>
          <Link to="/attendance" className="small">
            Open attendance →
          </Link>
        </section>
        <section className="card">
          <h2>Reports today</h2>
          <dl className="details">
            <div>
              <dt>Reports</dt>
              <dd>{dailyReports.reportsToday}</dd>
            </div>
            <div>
              <dt>Resumes</dt>
              <dd>{dailyReports.resumesToday}</dd>
            </div>
            <div>
              <dt>Not reported</dt>
              <dd>{dailyReports.employeesMissingToday}</dd>
            </div>
          </dl>
          <Link to="/performance-tracker" className="small">
            Open the tracker →
          </Link>
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
  const markedToday = attendance.todayStatus !== 'NOT_MARKED'

  return (
    <>
      <section className="card today-card">
        <div>
          <h2>Today · {formatDate(data.asOfDate ?? todayISO())}</h2>
          <p className="muted">
            {markedToday ? (
              <>
                You are marked <StatusBadge value={attendance.todayStatus} /> for today.
              </>
            ) : (
              'You haven’t marked your attendance yet.'
            )}
          </p>
        </div>
        <Link to="/attendance" className="btn btn-primary">
          <Icon name="clock" size={16} /> {markedToday ? 'Update attendance' : 'Mark attendance'}
        </Link>
      </section>

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
