import { useCallback, useMemo, useState } from 'react'
import { adminAttendanceApi, employeeAttendanceApi } from '../api/attendanceApi'
import { employeeLeaveApi } from '../api/leaveApi'
import { calendarApi } from '../api/calendarApi'
import { getErrorMessage } from '../api/axiosClient'
import { useAuth } from '../hooks/useAuth'
import { useFetch } from '../hooks/useFetch'
import { useToast } from '../hooks/useToast'
import PageHeader from '../components/common/PageHeader'
import StatusBadge from '../components/common/StatusBadge'
import DataTable from '../components/common/DataTable'
import AttendanceCalendar from '../components/common/AttendanceCalendar'
import CalendarEventsPanel from '../components/common/CalendarEventsPanel'
import ConfirmDialog from '../components/common/ConfirmDialog'
import { ErrorState, Spinner } from '../components/common/Feedback'
import { FilterSelect, SearchInput, Toolbar } from '../components/common/Toolbar'
import Icon from '../components/common/Icon'
import { formatDate, matchesQuery, todayISO } from '../utils/format'

export default function AttendancePage() {
  const { isAdmin } = useAuth()
  const api = isAdmin ? adminAttendanceApi : employeeAttendanceApi
  const toast = useToast()
  const fetcher = useCallback(() => api.list(), [api])
  const { data, loading, error, reload } = useFetch(fetcher)

  // Approved leave drives the red days on the calendar.
  const leaveFetcher = useCallback(
    () => (isAdmin ? Promise.resolve([]) : employeeLeaveApi.list().then((rows) => (rows ?? []).filter((l) => l.status === 'APPROVED'))),
    [isAdmin],
  )
  const { data: approvedLeaves, reload: reloadLeaves } = useFetch(leaveFetcher)

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

  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('')
  const [date, setDate] = useState('')
  const [busy, setBusy] = useState(false)
  const [removing, setRemoving] = useState(null)

  const today = todayISO()
  const todayRecord = (data ?? []).find((a) => a.attendanceDate === today)

  const monthStats = useMemo(() => {
    const month = today.slice(0, 7)
    const inMonth = (data ?? []).filter((a) => a.attendanceDate?.startsWith(month))
    return {
      present: inMonth.filter((a) => a.status === 'PRESENT').length,
      absent: inMonth.filter((a) => a.status === 'ABSENT').length,
      total: inMonth.length,
    }
  }, [data, today])

  const rows = useMemo(
    () =>
      (data ?? [])
        .filter(
          (a) =>
            (!status || a.status === status) &&
            (!date || a.attendanceDate === date) &&
            matchesQuery(a, query, ['employeeName']),
        )
        .sort((a, b) => b.attendanceDate.localeCompare(a.attendanceDate)),
    [data, query, status, date],
  )

  async function mark(next) {
    setBusy(true)
    try {
      if (todayRecord) await api.update(todayRecord.attendanceId, next)
      else await api.mark(next)
      toast.success(`Attendance marked ${next.toLowerCase()}`)
      await reload()
    } catch (e) {
      toast.error(getErrorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  async function updateAdmin(a) {
    setBusy(true)
    try {
      await adminAttendanceApi.update(a.attendanceId, a.status === 'PRESENT' ? 'ABSENT' : 'PRESENT')
      toast.success('Attendance updated')
      await reload()
    } catch (e) {
      toast.error(getErrorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  async function remove() {
    try {
      await adminAttendanceApi.remove(removing.attendanceId)
      toast.success('Attendance record deleted')
      setRemoving(null)
      await reload()
    } catch (e) {
      toast.error(getErrorMessage(e))
    }
  }

  async function refreshAll() {
    await Promise.all([reload(), reloadLeaves()])
  }

  // ---- ADMIN: team table (unchanged behaviour) ----
  const adminColumns = [
    { key: 'attendanceDate', label: 'Date', sortable: true, render: (a) => formatDate(a.attendanceDate) },
    { key: 'employeeName', label: 'Employee', sortable: true, render: (a) => <strong>{a.employeeName}</strong> },
    { key: 'status', label: 'Status', sortable: true, render: (a) => <StatusBadge value={a.status} /> },
    {
      key: 'actions',
      label: 'Actions',
      render: (a) => (
        <div className="row-actions">
          {a.attendanceDate === today && (
            <button type="button" className="btn btn-sm" disabled={busy} onClick={() => updateAdmin(a)}>
              Mark {a.status === 'PRESENT' ? 'absent' : 'present'}
            </button>
          )}
          <button type="button" className="btn btn-sm btn-danger-ghost" onClick={() => setRemoving(a)}>
            Delete
          </button>
        </div>
      ),
    },
  ]

  if (loading) return <Spinner />
  if (error) return <ErrorState message={error} onRetry={refreshAll} />

  if (isAdmin) {
    return (
      <>
        <PageHeader title="Attendance" subtitle="Attendance of your team. Only today’s records can be changed." />
        <div className="stats">
          <div className="card stat">
            <span className="stat-label">Present this month</span>
            <span className="stat-value stat-value--sm">{monthStats.present}</span>
          </div>
          <div className="card stat">
            <span className="stat-label">Absent this month</span>
            <span className="stat-value stat-value--sm">{monthStats.absent}</span>
          </div>
          <div className="card stat">
            <span className="stat-label">Records this month</span>
            <span className="stat-value stat-value--sm">{monthStats.total}</span>
          </div>
        </div>
        <section className="card">
          <Toolbar>
            <SearchInput value={query} onChange={setQuery} placeholder="Search employee…" />
            <FilterSelect
              label="Filter by status"
              value={status}
              onChange={setStatus}
              options={[
                { value: '', label: 'All statuses' },
                { value: 'PRESENT', label: 'Present' },
                { value: 'ABSENT', label: 'Absent' },
              ]}
            />
            <label className="filter">
              <span className="sr-only">Filter by date</span>
              <input type="date" value={date} onChange={(e) => setDate(e.target.value)} aria-label="Filter by date" />
            </label>
            {date && (
              <button type="button" className="btn btn-sm btn-ghost" onClick={() => setDate('')}>
                <Icon name="close" size={14} /> Clear date
              </button>
            )}
          </Toolbar>
          <DataTable
            columns={adminColumns}
            rows={rows}
            rowKey="attendanceId"
            emptyTitle={data.length ? 'No records match your filters' : 'No attendance records yet'}
            emptyHint={data.length ? undefined : 'Employees mark their own attendance; records appear here.'}
          />
        </section>
        {removing && (
          <ConfirmDialog
            title="Delete attendance record"
            message={`Delete the record for ${removing.employeeName} on ${formatDate(removing.attendanceDate)}? This cannot be undone.`}
            confirmLabel="Delete"
            danger
            onConfirm={remove}
            onCancel={() => setRemoving(null)}
          />
        )}
      </>
    )
  }

  // ---- EMPLOYEE: calendar ----
  return (
    <>
      <PageHeader title="Attendance" subtitle="Mark your attendance and see the whole month at a glance." />

      <section className="card today-card">
        <div>
          <h2>Today · {formatDate(today)}</h2>
          <p className="muted">
            {todayRecord ? (
              <>
                You are marked <StatusBadge value={todayRecord.status} />. You can change it until the day ends.
              </>
            ) : (
              'You haven’t marked attendance yet.'
            )}
          </p>
        </div>
        <div className="row-actions">
          <button
            type="button"
            className="btn btn-success"
            disabled={busy || todayRecord?.status === 'PRESENT'}
            onClick={() => mark('PRESENT')}
          >
            <Icon name="check" size={16} /> Mark present
          </button>
          <button type="button" className="btn" disabled={busy || todayRecord?.status === 'ABSENT'} onClick={() => mark('ABSENT')}>
            Mark absent
          </button>
        </div>
      </section>

      <div className="att-split">
        <div className="att-split__main">
          <AttendanceCalendar
            records={data ?? []}
            leaveRecords={approvedLeaves ?? []}
            holidayDateMap={holidayDateMap}
            today={today}
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

      <div className="stats">
        <div className="card stat">
          <span className="stat-label">Present this month</span>
          <span className="stat-value stat-value--sm">{monthStats.present}</span>
        </div>
        <div className="card stat">
          <span className="stat-label">Absent this month</span>
          <span className="stat-value stat-value--sm">{monthStats.absent}</span>
        </div>
        <div className="card stat">
          <span className="stat-label">Records this month</span>
          <span className="stat-value stat-value--sm">{monthStats.total}</span>
        </div>
      </div>
    </>
  )
}
