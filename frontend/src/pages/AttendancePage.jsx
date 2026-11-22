import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  adminAttendanceApi,
  adminAttendanceMonitorApi,
  employeeAttendanceApi,
  employeeSessionApi,
} from '../api/attendanceApi'
import { adminEmployeeApi } from '../api/adminApi'
import { employeeLeaveApi } from '../api/leaveApi'
import { employeeWfhApi } from '../api/wfhApi'
import { calendarApi } from '../api/calendarApi'
import { getErrorMessage } from '../api/axiosClient'
import { useAuth } from '../hooks/useAuth'
import { useFetch } from '../hooks/useFetch'
import { useToast } from '../hooks/useToast'
import PageHeader from '../components/common/PageHeader'
import StatusBadge from '../components/common/StatusBadge'
import DataTable from '../components/common/DataTable'
import Modal from '../components/common/Modal'
import StatCard from '../components/common/StatCard'
import AttendanceCalendar from '../components/common/AttendanceCalendar'
import CalendarEventsPanel from '../components/common/CalendarEventsPanel'
import ConfirmDialog from '../components/common/ConfirmDialog'
import FormModal from '../components/common/FormModal'
import { Alert, ErrorState, Spinner } from '../components/common/Feedback'
import { FilterSelect, SearchInput, Toolbar } from '../components/common/Toolbar'
import Icon from '../components/common/Icon'
import {
  formatDate,
  formatDateTimeIST,
  formatDuration,
  formatHours,
  formatTimeIST,
  secondsSince,
  titleCase,
  todayISO,
} from '../utils/format'

const WFH_FIELDS = [
  { name: 'startDate', label: 'Start date', type: 'date', required: true, half: true },
  { name: 'endDate', label: 'End date', type: 'date', required: true, half: true },
  { name: 'reason', label: 'Reason', type: 'textarea', required: true, maxLength: 2000 },
]

/** Attendance day statuses and how they read. Kept in one place so both views agree. */
const STATUS_LABELS = {
  LOGGED_IN: 'Logged in',
  LOGGED_OUT: 'Logged out',
  NOT_LOGGED_IN: 'Not logged in',
  INCOMPLETE_SESSION: 'Incomplete session',
  ABSENT: 'Absent',
  ON_LEAVE: 'On leave',
  HOLIDAY: 'Holiday',
  WEEKLY_OFF: 'Weekly off',
  PRESENT: 'Present (no login)',
}

const STATUS_TONES = {
  LOGGED_IN: 'green',
  LOGGED_OUT: 'blue',
  NOT_LOGGED_IN: 'gray',
  INCOMPLETE_SESSION: 'red',
  ABSENT: 'red',
  ON_LEAVE: 'blue',
  HOLIDAY: 'gray',
  WEEKLY_OFF: 'gray',
  PRESENT: 'green',
}

function StatusPill({ status }) {
  if (!status) return <span className="muted">—</span>
  const tone = STATUS_TONES[status] ?? 'gray'
  return <span className={`badge badge-${tone}`}>{STATUS_LABELS[status] ?? titleCase(status)}</span>
}

/** Live counter: ticks once a second while a session is open, else just shows a total. */
function useTicker(active) {
  const [nowMs, setNowMs] = useState(() => Date.now())

  useEffect(() => {
    if (!active) return undefined
    const id = setInterval(() => setNowMs(Date.now()), 1000)
    return () => clearInterval(id)
  }, [active])

  return nowMs
}

/* ------------------------------------------------------------------ */
/* EMPLOYEE                                                           */
/* ------------------------------------------------------------------ */

/**
 * Today's attendance: Log In / Log Out, the live duration of the open session,
 * today's sessions and the history of the month.
 *
 * The buttons write nothing but the action: every timestamp is taken from the server,
 * and the button is disabled while a request is in flight so a double click cannot
 * open two sessions.
 */
function EmployeeAttendance() {
  const toast = useToast()

  const todayFetcher = useCallback(() => employeeSessionApi.today(), [])
  const { data: today, loading, error, reload } = useFetch(todayFetcher)

  const monthStart = todayISO().slice(0, 8) + '01'
  const historyFetcher = useCallback(
    () => employeeSessionApi.history({ from: monthStart, to: todayISO() }),
    [monthStart],
  )
  const { data: history, error: historyError, reload: reloadHistory } = useFetch(historyFetcher)

  const [busy, setBusy] = useState(null) // 'login' | 'logout'
  const [actionError, setActionError] = useState('')
  const [notice, setNotice] = useState('')

  const open = Boolean(today?.loggedIn)
  const nowMs = useTicker(open)

  const liveSessionSeconds = open ? secondsSince(today.activeSince, nowMs) : 0
  const liveTotalSeconds = (today?.totalWorkSeconds ?? 0) + liveSessionSeconds

  async function act(action) {
    setBusy(action)
    setActionError('')
    setNotice('')

    try {
      const result =
        action === 'login' ? await employeeSessionApi.login() : await employeeSessionApi.logout()

      if (result.notice) setNotice(result.notice)
      toast.success(action === 'login' ? 'Logged in' : 'Logged out')
      await reload()
      await reloadHistory()
    } catch (e) {
      const message = getErrorMessage(e)
      setActionError(message)
      toast.error(message)
    } finally {
      setBusy(null)
    }
  }

  if (loading) return <Spinner />
  if (error) return <ErrorState message={error} onRetry={reload} />

  const dayStatus = today?.status

  const historyRows = (history?.days ?? []).slice().reverse()
  const historyDetail = (day) =>
    day.sessions.length === 0
      ? [['Sessions', 'No login recorded for this day']]
      : day.sessions.map((s) => [
          `Login ${formatTimeIST(s.loginAt)}`,
          secondsSince(s.loginAt) >= 0
            ? `${formatDateTimeIST(s.loginAt)} — ${
                s.logoutAt ? formatDateTimeIST(s.logoutAt) : 'no logout recorded'
              }${s.durationSeconds != null ? ` · ${formatDuration(s.durationSeconds)}` : ''}`
            : '',
        ])

  const historyColumns = [
    { key: 'date', label: 'Date', sortable: true, render: (d) => formatDate(d.date) },
    { key: 'status', label: 'Status', sortable: true, render: (d) => <StatusPill status={d.status} /> },
    { key: 'firstLoginAt', label: 'First login', render: (d) => formatTimeIST(d.firstLoginAt) },
    { key: 'lastLogoutAt', label: 'Last logout', render: (d) => formatTimeIST(d.lastLogoutAt) },
    {
      key: 'totalWorkSeconds',
      label: 'Worked',
      align: 'right',
      sortValue: (d) => d.totalWorkSeconds,
      render: (d) => formatDuration(d.totalWorkSeconds),
    },
    { key: 'sessionCount', label: 'Sessions', align: 'right', sortable: true },
  ]

  return (
    <>
      <section className="card att-today">
        <div className="att-today__main">
          <div className="att-today__head">
            <h2>Today · {formatDate(today.date)}</h2>
            <StatusPill status={dayStatus} />
          </div>

          <p className="muted">
            {open
              ? 'You are logged in. The timer counts the current session; log out when you stop working.'
              : today.sessionCount > 0
                ? 'You are logged out. Log in again to start another session — every session adds to today’s total.'
                : today.workingDay
                  ? 'You have not logged in yet today.'
                  : 'Today is a non-working day. You can still log in if you are working.'}
          </p>

          <dl className="att-figures">
            <div>
              <dt>First login</dt>
              <dd>{formatTimeIST(today.firstLoginAt)}</dd>
            </div>
            <div>
              <dt>Last logout</dt>
              <dd>{formatTimeIST(today.lastLogoutAt)}</dd>
            </div>
            <div>
              <dt>Total today</dt>
              <dd className="att-figures__strong">{formatDuration(liveTotalSeconds)}</dd>
            </div>
            <div>
              <dt>Current session</dt>
              <dd>{open ? formatDuration(liveSessionSeconds) : '—'}</dd>
            </div>
            <div>
              <dt>Sessions</dt>
              <dd>{today.sessionCount}</dd>
            </div>
            {today.workMode && (
              <div>
                <dt>Work mode</dt>
                <dd>{today.workMode === 'WFH' ? 'Work from home' : 'Office'}</dd>
              </div>
            )}
          </dl>

          {today.wfhApproved && (
            <Alert type="info">
              <Icon name="home" size={16} /> Approved work from home covers today — your login is recorded as WFH.
            </Alert>
          )}

          {notice && (
            <div role="status">
              <Alert type="warning">{notice}</Alert>
            </div>
          )}

          {actionError && (
            <div role="alert">
              <Alert>{actionError}</Alert>
            </div>
          )}
        </div>

        <div className="att-today__actions">
          {open ? (
            <button
              type="button"
              className="btn btn-danger"
              disabled={busy != null}
              aria-busy={busy === 'logout'}
              onClick={() => act('logout')}
            >
              <Icon name="clock" size={16} /> {busy === 'logout' ? 'Logging out…' : 'Log out'}
            </button>
          ) : (
            <button
              type="button"
              className="btn btn-success"
              disabled={busy != null}
              aria-busy={busy === 'login'}
              onClick={() => act('login')}
            >
              <Icon name="check" size={16} /> {busy === 'login' ? 'Logging in…' : 'Log in'}
            </button>
          )}
          <span className="muted small att-today__hint">
            {open ? `Open since ${formatTimeIST(today.activeSince)} IST` : 'Times shown in IST'}
          </span>
        </div>
      </section>

      <section className="card">
        <div className="att-section-head">
          <div>
            <h2>Today’s sessions</h2>
            <p className="muted small">
              Every login and logout of today, with the duration the server calculated.
            </p>
          </div>
          <span className="badge badge-green">
            {formatHours(liveTotalSeconds)} worked
          </span>
        </div>

        {today.sessions.length === 0 ? (
          <p className="muted small">No sessions yet today. Use Log in to start one.</p>
        ) : (
          <ul className="att-sessions">
            {today.sessions.map((s) => (
              <li key={s.sessionId}>
                <span className="att-sessions__times">
                  <strong>{formatTimeIST(s.loginAt)}</strong> →{' '}
                  {s.logoutAt ? <strong>{formatTimeIST(s.logoutAt)}</strong> : <em>open</em>}
                </span>
                {s.active ? (
                  <span className="badge badge-green">In progress · {formatDuration(liveSessionSeconds)}</span>
                ) : (
                  <span className="muted small">{formatDuration(s.durationSeconds)}</span>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="card">
        <div className="att-section-head">
          <div>
            <h2>Session history · {formatDate(monthStart)} – {formatDate(todayISO())}</h2>
            <p className="muted small">Open a day to see each login and logout. Times are IST.</p>
          </div>
          <span className="muted small">
            {history ? `${formatHours(history.totalWorkSeconds)} · ${history.sessionCount} session${history.sessionCount === 1 ? '' : 's'}` : ''}
          </span>
        </div>

        {historyError ? (
          <ErrorState message={historyError} onRetry={reloadHistory} />
        ) : (
          <DataTable
            columns={historyColumns}
            rows={historyRows}
            rowKey="date"
            pageSize={8}
            detail={historyDetail}
            emptyTitle="No attendance in this month yet"
            emptyHint="Your logins will appear here as soon as you start working."
          />
        )}
      </section>
    </>
  )
}



/* ------------------------------------------------------------------ */
/* ADMIN                                                              */
/* ------------------------------------------------------------------ */

function EmployeeHistoryModal({ employee, onClose, onChanged }) {
  const toast = useToast()
  const monthStart = todayISO().slice(0, 8) + '01'

  const historyFetcher = useCallback(
    () => adminAttendanceMonitorApi.employeeHistory(employee.employeeId, { from: monthStart, to: todayISO() }),
    [employee.employeeId, monthStart],
  )
  const { data: history, loading, error, reload } = useFetch(historyFetcher)

  // Today's monitor row, to offer the legacy record correction for today only.
  const todayRowFetcher = useCallback(
    () =>
      adminAttendanceMonitorApi.rows({ employeeId: employee.employeeId, from: todayISO(), to: todayISO() }),
    [employee.employeeId],
  )
  const { data: todayRows, reload: reloadTodayRows } = useFetch(todayRowFetcher)
  const todayRow = (todayRows ?? [])[0]

  const [busy, setBusy] = useState(false)

  async function correct(next) {
    const record = todayRow?.attendanceRecordId
    if (!record) return
    setBusy(true)
    try {
      await adminAttendanceApi.update(record, next)
      toast.success(`Today's record corrected to ${next === 'PRESENT' ? 'present' : 'absent'}`)
      onChanged?.()
      await reloadTodayRows()
      await reload()
    } catch (e) {
      toast.error(getErrorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={`Attendance history — ${employee.employeeName}`} onClose={onClose} wide>
      {loading ? (
        <Spinner />
      ) : error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : (
        <>
          <p className="muted small">
            {employee.employeeCode} · {employee.designation || '—'} · {formatDate(history.from)} –{' '}
            {formatDate(history.to)}
          </p>

          <div className="stats att-modal-stats">
            <div className="card stat">
              <span className="stat-label">Worked in range</span>
              <span className="stat-value stat-value--sm">{formatHours(history.totalWorkSeconds)}</span>
            </div>
            <div className="card stat">
              <span className="stat-label">Sessions</span>
              <span className="stat-value stat-value--sm">{history.sessionCount}</span>
            </div>
            <div className="card stat">
              <span className="stat-label">Days worked</span>
              <span className="stat-value stat-value--sm">{history.daysWorked}</span>
            </div>
            <div className="card stat">
              <span className="stat-label">Incomplete</span>
              <span className="stat-value stat-value--sm">{history.incompleteSessions}</span>
            </div>
          </div>

          {todayRow?.attendanceRecordId && (
            <div className="att-correct">
              <span className="muted small">
                Today&rsquo;s stored record is <strong>{STATUS_LABELS[todayRow.status] ?? todayRow.status}</strong>.
                A correction changes that record; the session log stays untouched.
              </span>
              <button
                type="button"
                className="btn btn-sm"
                disabled={busy}
                onClick={() => correct(todayRow.status === 'PRESENT' ? 'ABSENT' : 'PRESENT')}
              >
                Mark {todayRow.status === 'PRESENT' ? 'absent' : 'present'}
              </button>
            </div>
          )}

          <DataTable
            columns={[
              { key: 'date', label: 'Date', sortable: true, render: (d) => formatDate(d.date) },
              { key: 'status', label: 'Status', render: (d) => <StatusPill status={d.status} /> },
              { key: 'firstLoginAt', label: 'First login', render: (d) => formatTimeIST(d.firstLoginAt) },
              { key: 'lastLogoutAt', label: 'Last logout', render: (d) => formatTimeIST(d.lastLogoutAt) },
              {
                key: 'totalWorkSeconds',
                label: 'Worked',
                align: 'right',
                sortValue: (d) => d.totalWorkSeconds,
                render: (d) => formatDuration(d.totalWorkSeconds),
              },
              { key: 'sessionCount', label: 'Sessions', align: 'right', sortable: true },
            ]}
            rows={(history.days ?? []).slice().reverse()}
            rowKey="date"
            pageSize={7}
            detail={(d) =>
              d.sessions.length === 0
                ? [['Sessions', 'No login recorded for this day']]
                : d.sessions.map((s) => [
                    `Login ${formatTimeIST(s.loginAt)} → ${s.logoutAt ? formatTimeIST(s.logoutAt) : 'no logout'}`,
                    s.durationSeconds != null ? formatDuration(s.durationSeconds) : 'still open',
                  ])
            }
            emptyTitle="No attendance in this range"
          />
        </>
      )}
    </Modal>
  )
}

function AdminAttendanceMonitor() {
  const toast = useToast()

  const [from, setFrom] = useState(todayISO())
  const [to, setTo] = useState(todayISO())
  const [employeeId, setEmployeeId] = useState('')
  const [designation, setDesignation] = useState('')
  const [status, setStatus] = useState('')
  const [query, setQuery] = useState('')
  const [openEmployee, setOpenEmployee] = useState(null)
  const [exporting, setExporting] = useState(false)

  const params = useMemo(() => {
    const p = { from, to }
    if (employeeId) p.employeeId = employeeId
    if (designation) p.designation = designation
    if (status) p.status = status
    if (query.trim()) p.q = query.trim()
    return p
  }, [from, to, employeeId, designation, status, query])

  const rowsFetcher = useCallback(() => adminAttendanceMonitorApi.rows(params), [params])
  const { data: rows, loading, error, reload } = useFetch(rowsFetcher)

  const summaryFetcher = useCallback(() => adminAttendanceMonitorApi.summary({ from, to }), [from, to])
  const { data: summary, reload: reloadSummary } = useFetch(summaryFetcher)

  const activeFetcher = useCallback(() => adminAttendanceMonitorApi.active(), [])
  const { data: active, reload: reloadActive } = useFetch(activeFetcher)

  const employeesFetcher = useCallback(() => adminEmployeeApi.list(), [])
  const { data: employees } = useFetch(employeesFetcher)

  const nowMs = useTicker(Boolean(active && active.length > 0))

  const refreshAll = useCallback(async () => {
    await Promise.all([reload(), reloadSummary(), reloadActive()])
  }, [reload, reloadSummary, reloadActive])

  const designations = useMemo(
    () => [...new Set((employees ?? []).map((e) => e.role).filter(Boolean))].sort(),
    [employees],
  )

  const tableRows = useMemo(
    () => (rows ?? []).map((r) => ({ ...r, _key: `${r.employeeId}:${r.attendanceDate}` })),
    [rows],
  )

  const todayCounts = summary?.todayCounts

  async function exportCsv() {
    setExporting(true)
    try {
      const blob = await adminAttendanceMonitorApi.exportCsv(params)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `attendance-${from}-to-${to}.csv`
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
      toast.success('Attendance exported as CSV')
    } catch (e) {
      toast.error(getErrorMessage(e))
    } finally {
      setExporting(false)
    }
  }

  const columns = [
    { key: 'employeeCode', label: 'Employee ID', sortable: true },
    {
      key: 'employeeName',
      label: 'Employee',
      sortable: true,
      render: (r) => <strong>{r.employeeName}</strong>,
    },
    { key: 'designation', label: 'Designation', sortable: true },
    {
      key: 'attendanceDate',
      label: 'Date',
      sortable: true,
      render: (r) => formatDate(r.attendanceDate),
    },
    { key: 'firstLoginAt', label: 'First login', render: (r) => formatTimeIST(r.firstLoginAt) },
    { key: 'lastLogoutAt', label: 'Last logout', render: (r) => formatTimeIST(r.lastLogoutAt) },
    {
      key: 'totalWorkSeconds',
      label: 'Total hours',
      align: 'right',
      sortValue: (r) => r.totalWorkSeconds,
      render: (r) => formatDuration(r.totalWorkSeconds),
    },
    { key: 'sessionCount', label: 'Sessions', align: 'right', sortable: true },
    {
      key: 'status',
      label: 'Status',
      sortable: true,
      render: (r) => (
        <span className="att-status-cell">
          <StatusPill status={r.status} />
          {r.status === 'LOGGED_IN' && (
            <span className="muted small">
              since {formatTimeIST(r.openSince)} · {formatDuration(r.openSeconds)}
            </span>
          )}
        </span>
      ),
    },
    {
      key: 'actions',
      label: '',
      render: (r) => (
        <button
          type="button"
          className="btn btn-sm"
          onClick={() => setOpenEmployee(r)}
          aria-label={`View attendance history of ${r.employeeName}`}
        >
          History
        </button>
      ),
    },
  ]

  const rowDetail = (r) => [
    ['Working day', r.workingDay ? 'Yes' : 'No'],
    ['Work mode', r.wfhApproved ? 'Work from home' : r.workMode || 'Not logged in'],
    ['Approved work from home', r.wfhApproved ? 'Yes' : 'No'],
    ['Approved leave', r.onLeave ? 'Yes' : 'No'],
    ['Open session', r.openSince ? `started ${formatDateTimeIST(r.openSince)}` : 'None'],
    ['Attendance record', r.attendanceRecordId ? `#${r.attendanceRecordId} stored` : 'No stored record'],
  ]

  return (
    <>
      <div className="stats">
        <StatCard
          label="Logged in now"
          value={todayCounts?.loggedIn ?? '—'}
          hint={`${todayCounts?.activeEmployees ?? 0} active employees`}
          icon="users"
          tone="green"
        />
        <StatCard
          label="Logged out today"
          value={todayCounts?.loggedOut ?? '—'}
          hint={`${formatHours(todayCounts?.totalWorkSeconds ?? 0)} worked today`}
          icon="check"
          tone="blue"
        />
        <StatCard
          label="Not logged in"
          value={todayCounts?.notLoggedIn ?? '—'}
          hint={`${todayCounts?.onLeave ?? 0} on approved leave`}
          icon="clock"
        />
        <StatCard
          label="Incomplete sessions"
          value={todayCounts?.incompleteSessions ?? '—'}
          hint="Left open; open History to trace them"
          icon="alert"
          tone="red"
        />
      </div>

      {active && active.length > 0 && (
        <section className="card">
          <div className="att-section-head">
            <div>
              <h2>Currently logged in</h2>
              <p className="muted small">
                Open sessions right now. A session left open on an earlier day is flagged as
                incomplete — no logout time is ever invented.
              </p>
            </div>
            <span className="badge badge-green">{active.length} open</span>
          </div>
          <ul className="att-active">
            {active.map((r) => {
              const elapsed = r.status === 'INCOMPLETE_SESSION' ? 0 : secondsSince(r.openSince, nowMs)
              return (
                <li key={r.openSessionId}>
                  <div className="att-active__who">
                    <strong>{r.employeeName}</strong>
                    <span className="muted small">
                      {r.employeeCode} · {r.designation || '—'}
                    </span>
                  </div>
                  <div className="att-active__when">
                    {r.status === 'INCOMPLETE_SESSION' ? (
                      <>
                        <StatusPill status={r.status} />
                        <span className="muted small">
                          since {formatDate(r.attendanceDate)}, {formatTimeIST(r.openSince)}
                        </span>
                      </>
                    ) : (
                      <>
                        <span className="badge badge-green">live · {formatDuration(elapsed)}</span>
                        <span className="muted small">since {formatTimeIST(r.openSince)} IST</span>
                      </>
                    )}
                  </div>
                  <button type="button" className="btn btn-sm" onClick={() => setOpenEmployee(r)}>
                    History
                  </button>
                </li>
              )
            })}
          </ul>
        </section>
      )}

      <section className="card">
        <Toolbar right={<span className="muted small">{rows?.length ?? 0} rows</span>}>
          <label className="filter">
            <span className="sr-only">From date</span>
            <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} aria-label="From date" />
          </label>
          <span className="muted small">to</span>
          <label className="filter">
            <span className="sr-only">To date</span>
            <input type="date" value={to} onChange={(e) => setTo(e.target.value)} aria-label="To date" />
          </label>

          <FilterSelect
            label="Employee"
            value={employeeId}
            onChange={setEmployeeId}
            options={[
              { value: '', label: 'All employees' },
              ...(employees ?? []).map((e) => ({
                value: String(e.employeeId),
                label: `${e.employeeName} (${e.employeeCode})`,
              })),
            ]}
          />

          <FilterSelect
            label="Designation"
            value={designation}
            onChange={setDesignation}
            options={[
              { value: '', label: 'All designations' },
              ...designations.map((d) => ({ value: d, label: d })),
            ]}
          />

          <FilterSelect
            label="Status"
            value={status}
            onChange={setStatus}
            options={[
              { value: '', label: 'All statuses' },
              ...Object.entries(STATUS_LABELS).map(([value, label]) => ({ value, label })),
            ]}
          />

          <SearchInput value={query} onChange={setQuery} placeholder="Search name or ID…" />

          <button type="button" className="btn" disabled={exporting} onClick={exportCsv}>
            <Icon name="download" size={16} /> {exporting ? 'Exporting…' : 'Export CSV'}
          </button>
          <button type="button" className="btn btn-sm btn-ghost" onClick={refreshAll} title="Refresh">
            <Icon name="refresh" size={14} /> Refresh
          </button>
        </Toolbar>

        {loading ? (
          <Spinner />
        ) : error ? (
          <ErrorState message={error} onRetry={refreshAll} />
        ) : (
          <DataTable
            columns={columns}
            rows={tableRows}
            rowKey="_key"
            pageSize={12}
            detail={rowDetail}
            emptyTitle={rows?.length ? 'No rows match your filters' : 'No attendance in this range'}
            emptyHint={
              from === to
                ? 'No employee logged in on this date.'
                : 'No attendance sessions inside this date range.'
            }
          />
        )}
      </section>

      {openEmployee && (
        <EmployeeHistoryModal
          employee={openEmployee}
          onClose={() => setOpenEmployee(null)}
          onChanged={refreshAll}
        />
      )}
    </>
  )
}

/* ------------------------------------------------------------------ */
/* PAGE SHELL (employee keeps the WFH panel, calendar and WFH history) */
/* ------------------------------------------------------------------ */

export default function AttendancePage() {
  const { isAdmin } = useAuth()
  const toast = useToast()

  // Approved leave drives the red days on the calendar.
  const leaveFetcher = useCallback(
    () =>
      isAdmin
        ? Promise.resolve([])
        : employeeLeaveApi.list().then((rows) => (rows ?? []).filter((l) => l.status === 'APPROVED')),
    [isAdmin],
  )
  const { data: approvedLeaves, reload: reloadLeaves } = useFetch(leaveFetcher)

  // Employee WFH requests: the approved one covering today, plus the recent list.
  const todayFetcher = useCallback(() => (isAdmin ? Promise.resolve(null) : employeeWfhApi.today()), [isAdmin])
  const { data: wfhToday, reload: reloadWfhToday } = useFetch(todayFetcher)

  const wfhListFetcher = useCallback(() => (isAdmin ? Promise.resolve([]) : employeeWfhApi.list()), [isAdmin])
  const { data: wfhRequests, reload: reloadWfh } = useFetch(wfhListFetcher)

  // Stored daily records feed the calendar cells (PRESENT/ABSENT history).
  const recordsFetcher = useCallback(
    () => (isAdmin ? Promise.resolve([]) : employeeAttendanceApi.list()),
    [isAdmin],
  )
  const { data: records } = useFetch(recordsFetcher)

  // Month shown in the calendar; the events panel follows it.
  const [month, setMonth] = useState(() => todayISO().slice(0, 7))
  const eventsFetcher = useCallback(() => calendarApi.events(month), [month])
  const { data: eventsData, loading: eventsLoading, error: eventsError, reload: reloadEvents } = useFetch(eventsFetcher)

  const holidayDateMap = useMemo(() => {
    const map = new Map()
    for (const e of eventsData?.events ?? []) {
      if (e.type === 'HOLIDAY') map.set(e.date, e.title)
    }
    return map
  }, [eventsData])

  const [wfhForm, setWfhForm] = useState(false)
  const [cancellingWfh, setCancellingWfh] = useState(null)

  const approvedWfhRecords = useMemo(
    () => (wfhRequests ?? []).filter((w) => w.status === 'APPROVED'),
    [wfhRequests],
  )

  const recentWfh = useMemo(
    () => (wfhRequests ?? []).slice().sort((a, b) => b.startDate.localeCompare(a.startDate)).slice(0, 4),
    [wfhRequests],
  )

  async function refreshAll() {
    await Promise.all([reloadLeaves(), reloadWfhToday(), reloadWfh(), reloadEvents()])
  }

  async function submitWfh(values) {
    await employeeWfhApi.apply({
      startDate: values.startDate,
      endDate: values.endDate,
      reason: values.reason.trim(),
    })
    toast.success('Work-from-home request submitted')
    setWfhForm(false)
    await refreshAll()
  }

  async function cancelWfh() {
    try {
      await employeeWfhApi.cancel(cancellingWfh.wfhId)
      toast.success('WFH request cancelled')
      setCancellingWfh(null)
      await refreshAll()
    } catch (e) {
      toast.error(getErrorMessage(e))
    }
  }

  if (isAdmin) {
    return (
      <>
        <PageHeader
          title="Attendance"
          subtitle="Login and logout sessions of your team: first login, last logout and working hours per day. Statuses are derived from the session log, never marked by hand."
          actions={
            <Link className="btn btn-secondary" to="/wfh">
              <Icon name="home" size={16} /> WFH requests
            </Link>
          }
        />
        <AdminAttendanceMonitor />
      </>
    )
  }

  return (
    <>
      <PageHeader
        title="Attendance"
        subtitle="Log in when you start work and log out when you stop — every session counts once towards your working time."
        actions={
          <button type="button" className="btn btn-secondary" onClick={() => setWfhForm(true)}>
            <Icon name="home" size={16} /> Request work from home
          </button>
        }
      />

      <EmployeeAttendance />

      <section className="card wfh-panel">
        <div className="wfh-panel__head">
          <div>
            <h2>Work from home</h2>
            <p className="muted small">
              {wfhToday?.approved
                ? 'Approved for today — your login is recorded as WFH automatically.'
                : wfhToday?.pendingCount
                  ? `${wfhToday.pendingCount} request${wfhToday.pendingCount === 1 ? '' : 's'} awaiting review.`
                  : 'No approved WFH today.'}
            </p>
          </div>
          <div className="row-actions">
            <button type="button" className="btn btn-sm btn-primary" onClick={() => setWfhForm(true)}>
              <Icon name="plus" size={14} /> Request WFH
            </button>
            <Link className="btn btn-sm" to="/wfh">
              History
            </Link>
          </div>
        </div>

        {recentWfh.length === 0 ? (
          <p className="muted small">No work-from-home requests yet.</p>
        ) : (
          <ul className="wfh-list">
            {recentWfh.map((w) => (
              <li key={w.wfhId}>
                <span className="wfh-list__dates">
                  {formatDate(w.startDate)}
                  {w.endDate !== w.startDate && ` – ${formatDate(w.endDate)}`}
                </span>
                <StatusBadge value={w.status} />
                {w.status === 'PENDING' && (
                  <button type="button" className="btn btn-sm btn-danger-ghost" onClick={() => setCancellingWfh(w)}>
                    Cancel
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <div className="att-split">
        <div className="att-split__main">
          <AttendanceCalendar
            records={records ?? []}
            leaveRecords={approvedLeaves ?? []}
            wfhRecords={approvedWfhRecords}
            holidayDateMap={holidayDateMap}
            today={wfhToday?.date ?? todayISO()}
            onMonthChange={setMonth}
          />
        </div>
        <CalendarEventsPanel
          data={eventsData}
          loading={eventsLoading}
          error={eventsError}
          onRetry={reloadEvents}
        />
      </div>

      {wfhForm && (
        <FormModal
          title="Request work from home"
          fields={WFH_FIELDS.map((f) => (f.type === 'date' ? { ...f, min: todayISO() } : f))}
          initialValues={{}}
          validate={(v) =>
            v.startDate && v.endDate && v.endDate < v.startDate
              ? { endDate: 'End date cannot be before start date' }
              : {}
          }
          submitLabel="Submit request"
          onSubmit={submitWfh}
          onClose={() => setWfhForm(false)}
          intro={
            <div className="alert alert-info">
              <Icon name="info" size={16} />
              <span>
                An administrator must approve your request. Approval records your login as WFH;
                working hours still come from the login sessions.
              </span>
            </div>
          }
        />
      )}

      {cancellingWfh && (
        <ConfirmDialog
          title="Cancel WFH request"
          message={`Cancel your work-from-home request from ${formatDate(cancellingWfh.startDate)} to ${formatDate(
            cancellingWfh.endDate,
          )}?`}
          confirmLabel="Cancel request"
          danger
          onConfirm={cancelWfh}
          onCancel={() => setCancellingWfh(null)}
        />
      )}
    </>
  )
}
