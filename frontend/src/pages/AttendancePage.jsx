import { useCallback, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { adminAttendanceApi, employeeAttendanceApi } from '../api/attendanceApi'
import { employeeLeaveApi } from '../api/leaveApi'
import { employeeWfhApi } from '../api/wfhApi'
import { employeeOfficeApi } from '../api/officeApi'
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
import FormModal from '../components/common/FormModal'
import { Alert, ErrorState, Spinner } from '../components/common/Feedback'
import { FilterSelect, SearchInput, Toolbar } from '../components/common/Toolbar'
import Icon from '../components/common/Icon'
import { formatDate, matchesQuery, titleCase, todayISO } from '../utils/format'
import { getBrowserLocation } from '../utils/geolocation'

const WFH_FIELDS = [
  { name: 'startDate', label: 'Start date', type: 'date', required: true, half: true },
  { name: 'endDate', label: 'End date', type: 'date', required: true, half: true },
  { name: 'reason', label: 'Reason', type: 'textarea', required: true, maxLength: 2000 },
]

function presenceLabel(record) {
  if (!record) return ''
  if (record.status !== 'PRESENT') return titleCase(record.status)
  if (record.workMode === 'WFH') return 'Present — WFH'
  if (record.workMode === 'OFFICE') return 'Present — Office'
  return 'Present'
}

function presenceTone(record) {
  if (!record) return undefined
  return record.status === 'PRESENT' && record.workMode === 'WFH' ? 'WFH' : record.status
}

export default function AttendancePage() {
  const { isAdmin } = useAuth()
  const api = isAdmin ? adminAttendanceApi : employeeAttendanceApi
  const toast = useToast()

  const fetcher = useCallback(() => api.list(), [api])
  const { data, loading, error, reload } = useFetch(fetcher)

  // Approved leave drives the red days on the calendar.
  const leaveFetcher = useCallback(
    () =>
      isAdmin
        ? Promise.resolve([])
        : employeeLeaveApi.list().then((rows) => (rows ?? []).filter((l) => l.status === 'APPROVED')),
    [isAdmin],
  )
  const { data: approvedLeaves, reload: reloadLeaves } = useFetch(leaveFetcher)

  // Employee WFH position and history; admins load the company queue for mode labels.
  const todayFetcher = useCallback(() => (isAdmin ? Promise.resolve(null) : employeeWfhApi.today()), [isAdmin])
  const { data: wfhToday, reload: reloadWfhToday } = useFetch(todayFetcher)

  const wfhListFetcher = useCallback(
    () => (isAdmin ? Promise.resolve([]) : employeeWfhApi.list()),
    [isAdmin],
  )
  const { data: wfhRequests, reload: reloadWfh } = useFetch(wfhListFetcher)

  const officeFetcher = useCallback(
    () => (isAdmin ? Promise.resolve(null) : employeeOfficeApi.get()),
    [isAdmin],
  )
  const { data: office, reload: reloadOffice } = useFetch(officeFetcher)

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
  // null | 'locating' | 'submitting-present' | 'submitting-absent'
  const [markPhase, setMarkPhase] = useState(null)
  const [locationError, setLocationError] = useState('')
  const [removing, setRemoving] = useState(null)
  const [wfhForm, setWfhForm] = useState(false)
  const [cancellingWfh, setCancellingWfh] = useState(null)

  // The server's company-local date wins over the browser's date near midnight.
  const today = wfhToday?.date ?? todayISO()
  const todayRecord = (data ?? []).find((a) => a.attendanceDate === today)
  const approvedWfhToday = Boolean(!isAdmin && wfhToday?.approved)

  const approvedWfhRecords = useMemo(
    () => (wfhRequests ?? []).filter((w) => w.status === 'APPROVED'),
    [wfhRequests],
  )

  const recentWfh = useMemo(
    () => (wfhRequests ?? []).slice().sort((a, b) => b.startDate.localeCompare(a.startDate)).slice(0, 4),
    [wfhRequests],
  )

  const monthStats = useMemo(() => {
    const currentMonth = today.slice(0, 7)
    const inMonth = (data ?? []).filter((a) => a.attendanceDate?.startsWith(currentMonth))
    return {
      present: inMonth.filter((a) => a.status === 'PRESENT').length,
      wfh: inMonth.filter((a) => a.status === 'PRESENT' && a.workMode === 'WFH').length,
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
    setLocationError('')

    if (next === 'PRESENT' && !approvedWfhToday) {
      setMarkPhase('locating')
      let location
      try {
        location = await getBrowserLocation()
      } catch (e) {
        setLocationError(e.message)
        toast.error(e.message)
        setMarkPhase(null)
        return
      }

      setMarkPhase('submitting-present')
      try {
        if (todayRecord) await api.update(todayRecord.attendanceId, next, location)
        else await api.mark(next, location)
        toast.success('Attendance marked present — Office')
        await refreshAll()
      } catch (e) {
        const message = getErrorMessage(e)
        setLocationError(message)
        toast.error(message)
      } finally {
        setMarkPhase(null)
      }
      return
    }

    setMarkPhase(next === 'PRESENT' ? 'submitting-present' : 'submitting-absent')
    try {
      if (todayRecord) await api.update(todayRecord.attendanceId, next)
      else await api.mark(next)
      toast.success(
        next === 'PRESENT'
          ? approvedWfhToday
            ? 'Attendance marked present — WFH'
            : 'Attendance marked present'
          : 'Attendance marked absent',
      )
      await refreshAll()
    } catch (e) {
      const message = getErrorMessage(e)
      setLocationError(message)
      toast.error(message)
    } finally {
      setMarkPhase(null)
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

  async function refreshAll() {
    await Promise.all([reload(), reloadLeaves(), reloadWfhToday(), reloadWfh(), reloadOffice()])
  }

  // ---- ADMIN: team table ----
  const adminColumns = [
    { key: 'attendanceDate', label: 'Date', sortable: true, render: (a) => formatDate(a.attendanceDate) },
    { key: 'employeeName', label: 'Employee', sortable: true, render: (a) => <strong>{a.employeeName}</strong> },
    {
      key: 'status',
      label: 'Attendance',
      sortable: true,
      render: (a) => (
        <span className="att-mode">
          <StatusBadge value={presenceTone(a)} label={presenceLabel(a)} />
          {a.workMode === 'OFFICE' && a.distanceMeters != null && (
            <span className="muted small">within {Math.round(a.distanceMeters)} m</span>
          )}
        </span>
      ),
    },
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
        <PageHeader
          title="Attendance"
          subtitle="Attendance of your team. Office present records include the verified distance; only today’s records can be changed."
          actions={
            <Link className="btn btn-secondary" to="/wfh">
              <Icon name="home" size={16} /> WFH requests
            </Link>
          }
        />
        <div className="stats">
          <div className="card stat">
            <span className="stat-label">Present this month</span>
            <span className="stat-value stat-value--sm">{monthStats.present}</span>
          </div>
          <div className="card stat">
            <span className="stat-label">Present — WFH</span>
            <span className="stat-value stat-value--sm">{monthStats.wfh}</span>
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

  // ---- EMPLOYEE: today + WFH + calendar ----
  const markPresentLabel =
    markPhase === 'locating'
      ? 'Checking location…'
      : markPhase === 'submitting-present'
        ? 'Marking present…'
        : 'Mark present'

  return (
    <>
      <PageHeader
        title="Attendance"
        subtitle="Mark your attendance and see the whole month at a glance."
        actions={
          <button type="button" className="btn btn-secondary" onClick={() => setWfhForm(true)}>
            <Icon name="home" size={16} /> Request work from home
          </button>
        }
      />

      <section className="card today-card">
        <div>
          <h2>Today · {formatDate(today)}</h2>
          <p className="muted">
            {todayRecord ? (
              <>
                You are marked <StatusBadge value={presenceTone(todayRecord)} label={presenceLabel(todayRecord)} />.
                You can change it until the day ends.
              </>
            ) : approvedWfhToday ? (
              'Work from home is approved for today. Mark present without sharing a location.'
            ) : (
              'You haven’t marked attendance yet.'
            )}
          </p>
          {approvedWfhToday && (
            <div className="today-wfh">
              <Alert type="info">
                <Icon name="home" size={16} /> Approved WFH · {formatDate(wfhToday.startDate)}
                {wfhToday.endDate !== wfhToday.startDate && ` – ${formatDate(wfhToday.endDate)}`}. Marking present
                records <strong>Present — WFH</strong>; it does not mark attendance automatically.
              </Alert>
            </div>
          )}
          {!approvedWfhToday && office && office.configured === false && (
            <div className="today-wfh">
              <Alert type="warning">
                Office location is not configured. Ask your administrator to set it before marking office attendance.
              </Alert>
            </div>
          )}
          {locationError && (
            <div className="today-wfh" role="alert">
              <Alert>{locationError}</Alert>
            </div>
          )}
        </div>
        <div className="row-actions">
          <button
            type="button"
            className="btn btn-success"
            disabled={markPhase != null || busy || todayRecord?.status === 'PRESENT'}
            onClick={() => mark('PRESENT')}
            aria-busy={markPhase != null}
          >
            <Icon name={markPhase === 'locating' ? 'pin' : 'check'} size={16} /> {markPresentLabel}
          </button>
          <button
            type="button"
            className="btn"
            disabled={markPhase != null || busy || todayRecord?.status === 'ABSENT'}
            onClick={() => mark('ABSENT')}
          >
            {markPhase === 'submitting-absent' ? 'Marking absent…' : 'Mark absent'}
          </button>
        </div>
      </section>

      <section className="card wfh-panel">
        <div className="wfh-panel__head">
          <div>
            <h2>Work from home</h2>
            <p className="muted small">
              {approvedWfhToday
                ? 'Approved for today.'
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
                  <button
                    type="button"
                    className="btn btn-sm btn-danger-ghost"
                    onClick={() => setCancellingWfh(w)}
                  >
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
            records={data ?? []}
            leaveRecords={approvedLeaves ?? []}
            wfhRecords={approvedWfhRecords}
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
          <span className="stat-label">Present — WFH</span>
          <span className="stat-value stat-value--sm">{monthStats.wfh}</span>
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

      {wfhForm && (
        <FormModal
          title="Request work from home"
          fields={WFH_FIELDS.map((f) => (f.type === 'date' ? { ...f, min: today } : f))}
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
                An administrator must approve your request. Home coordinates are never requested; approval only lets
                you mark present without an office location check.
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
