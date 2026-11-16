import { Link } from 'react-router-dom'
import { adminDashboardApi } from '../api/dashboardApi'
import { employeePortalApi } from '../api/employeeApi'
import { useAuth } from '../hooks/useAuth'
import { useFetch } from '../hooks/useFetch'
import PageHeader from '../components/common/PageHeader'
import StatCard from '../components/common/StatCard'
import StatusBadge from '../components/common/StatusBadge'
import Skeleton from '../components/common/Skeleton'
import { EmptyState, ErrorState } from '../components/common/Feedback'
import Icon from '../components/common/Icon'
import { formatDate, formatMoney, formatNumber, todayISO } from '../utils/format'

export default function DashboardPage() {
  const { user, isAdmin } = useAuth()
  const firstName = user.name?.split(' ')[0] ?? 'there'

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
      />
      {isAdmin ? <AdminDashboard /> : <EmployeeDashboard />}
    </>
  )
}

/* ------------------------------- Admin ------------------------------- */

function AdminDashboard() {
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

  const { people, attendanceToday, leaves, clients, pendingLeaveQueue, pendingEmployees } = data

  return (
    <>
      <div className="stats">
        <StatCard
          label="Active employees"
          value={formatNumber(people.activeEmployees)}
          hint={`${people.totalEmployees} total · ${people.pendingEmployees} awaiting approval`}
          icon="users"
          to="/employees"
        />
        <StatCard
          label="Present today"
          value={formatNumber(attendanceToday.presentToday)}
          hint={`${attendanceToday.markedToday} marked · ${attendanceToday.notMarkedToday} not marked`}
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
          label="Active clients"
          value={formatNumber(clients.activeClients)}
          hint={`${clients.totalClients} total`}
          icon="briefcase"
          to="/clients"
        />
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
            <h2>New sign-ups</h2>
            <Link to="/employees" className="small">
              Manage team →
            </Link>
          </div>
          {pendingEmployees.length === 0 ? (
            <EmptyState icon="user-check" title="No pending sign-ups" hint="New registrations appear here for approval." />
          ) : (
            <ul className="list">
              {pendingEmployees.map((e) => (
                <li key={e.employeeId}>
                  <div style={{ minWidth: 0 }}>
                    <strong>{e.employeeName}</strong>
                    <div className="muted small">
                      {e.role} · {e.email}
                    </div>
                  </div>
                  <StatusBadge value="PENDING" />
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
          <h2>Quick actions</h2>
          <div style={{ display: 'grid', gap: 8 }}>
            <Link to="/employees" className="btn btn-block">
              <Icon name="users" size={16} /> Approve employees
            </Link>
            <Link to="/leaves" className="btn btn-block">
              <Icon name="calendar" size={16} /> Review leave
            </Link>
            <Link to="/reports" className="btn btn-block">
              <Icon name="file" size={16} /> Team daily reports
            </Link>
          </div>
        </section>
      </div>
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
    </>
  )
}
