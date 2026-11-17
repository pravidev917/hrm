import { useCallback, useMemo, useState } from 'react'
import { adminLeaveApi, employeeLeaveApi } from '../api/leaveApi'
import { useAuth } from '../hooks/useAuth'
import { useFetch } from '../hooks/useFetch'
import { useToast } from '../hooks/useToast'
import PageHeader from '../components/common/PageHeader'
import StatusBadge from '../components/common/StatusBadge'
import DataTable from '../components/common/DataTable'
import FormModal from '../components/common/FormModal'
import ConfirmDialog from '../components/common/ConfirmDialog'
import Skeleton from '../components/common/Skeleton'
import { EmptyState, ErrorState, Spinner } from '../components/common/Feedback'
import { FilterSelect, SearchInput, Toolbar } from '../components/common/Toolbar'
import Icon from '../components/common/Icon'
import { formatDate, formatDateTime, formatMoney, matchesQuery, titleCase, todayISO } from '../utils/format'

// Values accepted by the backend (LeaveManagementService).
const LEAVE_TYPES = ['CASUAL', 'SICK', 'ANNUAL', 'EMERGENCY', 'UNPAID'].map((v) => ({ value: v, label: titleCase(v) }))
const HALF_DAY = [
  { value: 'FIRST_HALF', label: 'First half' },
  { value: 'SECOND_HALF', label: 'Second half' },
]
const STATUS_OPTIONS = [
  { value: '', label: 'All statuses' },
  ...['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'].map((s) => ({ value: s, label: titleCase(s) })),
]

const LEAVE_FIELDS = [
  { name: 'type', label: 'Leave type', type: 'select', options: LEAVE_TYPES, required: true, half: true },
  { name: 'halfDayPeriod', label: 'Half day (optional)', type: 'select', options: HALF_DAY, half: true, placeholder: 'Full day', hint: 'Half-day leave must be a single date' },
  { name: 'startDate', label: 'Start date', type: 'date', min: todayISO(), required: true, half: true },
  { name: 'endDate', label: 'End date', type: 'date', min: todayISO(), required: true, half: true },
  { name: 'reason', label: 'Reason', type: 'textarea', required: true, maxLength: 2000 },
]

const REVIEW_FIELDS = [
  { name: 'status', label: 'Decision', type: 'select', required: true, options: [{ value: 'APPROVED', label: 'Approve' }, { value: 'REJECTED', label: 'Reject' }] },
  { name: 'remarks', label: 'Remarks', type: 'textarea', maxLength: 2000, hint: 'Required when rejecting' },
]

function validateLeave(v) {
  const errors = {}
  if (v.startDate && v.endDate && v.endDate < v.startDate) errors.endDate = 'End date cannot be before start date'
  if (v.halfDayPeriod && v.startDate && v.endDate && v.startDate !== v.endDate)
    errors.halfDayPeriod = 'Half-day leave must be for a single date'
  return errors
}

/** Working days (Mon–Sat) between two dates, mirroring the backend rule (Sunday excluded). */
function workingDaysBetween(start, end) {
  if (!start || !end || end < start) return null
  let days = 0
  const cursor = new Date(`${start}T00:00:00`)
  const last = new Date(`${end}T00:00:00`)
  while (cursor <= last) {
    if (cursor.getDay() !== 0) days++
    cursor.setDate(cursor.getDate() + 1)
  }
  return days
}

export default function LeavesPage() {
  const { isAdmin } = useAuth()
  const toast = useToast()
  const fetcher = useCallback(() => (isAdmin ? adminLeaveApi.list() : employeeLeaveApi.list()), [isAdmin])
  const { data, loading, error, reload } = useFetch(fetcher)
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('')
  const [form, setForm] = useState(null) // { leave? }
  const [review, setReview] = useState(null)
  const [cancelling, setCancelling] = useState(null)

  const rows = useMemo(
    () =>
      (data ?? [])
        .filter((l) => (!status || l.status === status) && matchesQuery(l, query, ['employeeName', 'type', 'reason', 'adminRemarks']))
        .sort((a, b) => b.startDate.localeCompare(a.startDate)),
    [data, query, status],
  )

  const counts = useMemo(() => {
    const base = { PENDING: 0, APPROVED: 0, REJECTED: 0, CANCELLED: 0 }
    for (const l of data ?? []) if (base[l.status] != null) base[l.status] += 1
    return base
  }, [data])

  const columns = [
    ...(isAdmin ? [{ key: 'employeeName', label: 'Employee', sortable: true, render: (l) => <strong>{l.employeeName}</strong> }] : []),
    { key: 'type', label: 'Type', sortable: true, render: (l) => titleCase(l.type) },
    {
      key: 'startDate',
      label: 'Dates',
      sortable: true,
      render: (l) => (
        <>
          {formatDate(l.startDate)}
          {l.endDate !== l.startDate && <> – {formatDate(l.endDate)}</>}
          {l.halfDayPeriod && <div className="muted small">{titleCase(l.halfDayPeriod)}</div>}
        </>
      ),
    },
    { key: 'numberOfDays', label: 'Days', sortable: true, align: 'right', render: (l) => formatMoney(l.numberOfDays) },
    { key: 'reason', label: 'Reason', render: (l) => <span className="clamp" title={l.reason}>{l.reason}</span> },
    { key: 'status', label: 'Status', sortable: true, render: (l) => <StatusBadge value={l.status} /> },
    {
      key: 'adminRemarks',
      label: 'Decision',
      render: (l) =>
        l.adminRemarks || l.reviewedAt ? (
          <div>
            {l.adminRemarks && <span className="clamp" title={l.adminRemarks}>{l.adminRemarks}</span>}
            {l.reviewedAt && <div className="muted small">{formatDateTime(l.reviewedAt)}</div>}
          </div>
        ) : (
          '—'
        ),
    },
    {
      key: 'actions',
      label: 'Actions',
      render: (l) =>
        l.status !== 'PENDING' ? null : isAdmin ? (
          <button type="button" className="btn btn-sm btn-primary" onClick={() => setReview(l)}>
            Review
          </button>
        ) : (
          <div className="row-actions">
            <button type="button" className="btn btn-sm" onClick={() => setForm({ leave: l })}>
              Edit
            </button>
            <button type="button" className="btn btn-sm btn-danger-ghost" onClick={() => setCancelling(l)}>
              Cancel
            </button>
          </div>
        ),
    },
  ]

  async function saveLeave(v) {
    const body = {
      type: v.type,
      startDate: v.startDate,
      endDate: v.endDate,
      reason: v.reason.trim(),
      halfDayPeriod: v.halfDayPeriod || null,
    }
    if (form.leave) {
      await employeeLeaveApi.update(form.leave.leaveId, body)
      toast.success('Leave request updated')
    } else {
      await employeeLeaveApi.apply(body)
      toast.success('Leave request submitted')
    }
    setForm(null)
    reload()
  }

  async function submitReview(v) {
    await adminLeaveApi.review(review.leaveId, { status: v.status, remarks: v.remarks.trim() || null })
    toast.success(`Leave ${v.status.toLowerCase()}`)
    setReview(null)
    reload()
  }

  async function cancel() {
    await employeeLeaveApi.cancel(cancelling.leaveId)
    toast.success('Leave request cancelled')
    setCancelling(null)
    reload()
  }

  return (
    <>
      <PageHeader
        title={isAdmin ? 'Leave requests' : 'My leave'}
        subtitle={
          isAdmin
            ? 'Review time-off requests from your team.'
            : 'Request time off and follow every decision in one place.'
        }
        actions={
          !isAdmin && (
            <button type="button" className="btn btn-primary" onClick={() => setForm({})}>
              <Icon name="plus" size={16} /> Request leave
            </button>
          )
        }
      />

      {!isAdmin && <LeaveBalancePanel reloadKey={data} />}

      <div className={isAdmin ? undefined : 'grid-sidebar'}>
        <section className="card">
          <div className="stats" style={{ marginBottom: 16 }}>
            {['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'].map((s) => (
              <button
                key={s}
                type="button"
                className="card stat stat-link"
                style={{
                  textAlign: 'left',
                  cursor: 'pointer',
                  borderColor: status === s ? '#c7d2fe' : undefined,
                  background: status === s ? 'var(--primary-soft)' : undefined,
                }}
                onClick={() => setStatus((cur) => (cur === s ? '' : s))}
                aria-pressed={status === s}
              >
                <span className="stat-label">{titleCase(s)}</span>
                <span className="stat-value" style={{ fontSize: '1.5rem' }}>
                  {loading ? '—' : counts[s]}
                </span>
              </button>
            ))}
          </div>

          <Toolbar>
            <SearchInput
              value={query}
              onChange={setQuery}
              placeholder={isAdmin ? 'Search employee, type, reason…' : 'Search type, reason…'}
            />
            <FilterSelect label="Filter by status" value={status} onChange={setStatus} options={STATUS_OPTIONS} />
          </Toolbar>

          {loading ? (
            <Spinner />
          ) : error ? (
            <ErrorState message={error} onRetry={reload} />
          ) : (
            <DataTable
              columns={columns}
              rows={rows}
              rowKey="leaveId"
              emptyTitle={data.length ? 'No requests match your filters' : 'No leave requests yet'}
              emptyHint={!isAdmin && !data.length ? 'Use “Request leave” to submit your first request.' : undefined}
            />
          )}
        </section>

        {!isAdmin && <ApprovalHistory />}
      </div>

      {form && (
        <FormModal
          title={form.leave ? 'Edit leave request' : 'Request leave'}
          fields={LEAVE_FIELDS}
          initialValues={form.leave ?? {}}
          validate={validateLeave}
          submitLabel={form.leave ? 'Save changes' : 'Submit request'}
          onSubmit={saveLeave}
          onClose={() => setForm(null)}
          hint={(v) => {
            // Show the day count the server will compute, so the user is not surprised.
            const days = v.halfDayPeriod ? 0.5 : workingDaysBetween(v.startDate, v.endDate)
            return days == null ? null : `${days} working day${days === 1 ? '' : 's'} will be requested (Sundays excluded)`
          }}
        />
      )}

      {review && (
        <FormModal
          title={`Review leave · ${review.employeeName}`}
          fields={REVIEW_FIELDS}
          initialValues={{ status: 'APPROVED', remarks: '' }}
          validate={(v) => (v.status === 'REJECTED' && !v.remarks.trim() ? { remarks: 'Remarks are required when rejecting' } : {})}
          submitLabel="Submit decision"
          onSubmit={submitReview}
          onClose={() => setReview(null)}
          intro={
            <div className="alert alert-info">
              <Icon name="calendar" size={16} />
              <span>
                {review.employeeName} · {titleCase(review.type)} · {formatDate(review.startDate)}
                {review.endDate !== review.startDate && ` – ${formatDate(review.endDate)}`} ·{' '}
                {formatMoney(review.numberOfDays)} day(s)
                {review.reason && <div className="small">“{review.reason}”</div>}
              </span>
            </div>
          }
        />
      )}

      {cancelling && (
        <ConfirmDialog
          title="Cancel leave request"
          message={`Cancel your ${titleCase(cancelling.type)} leave from ${formatDate(cancelling.startDate)}?`}
          confirmLabel="Cancel request"
          danger
          onConfirm={cancel}
          onCancel={() => setCancelling(null)}
        />
      )}
    </>
  )
}

/** Employee leave position, computed by the backend from stored records. */
function LeaveBalancePanel({ reloadKey }) {
  const fetcher = useCallback(() => employeeLeaveApi.balance(), [])
  const { data, loading, error, reload } = useFetch(fetcher)
  // Refetch when the leave list changes (e.g. after a new request).
  const [lastKey, setLastKey] = useState(reloadKey)
  if (lastKey !== reloadKey) {
    setLastKey(reloadKey)
    reload()
  }

  if (error) {
    return (
      <section className="card">
        <h2>Leave balance</h2>
        <ErrorState title="Balance unavailable" message={error} onRetry={reload} />
      </section>
    )
  }

  return (
    <section className="card">
      <div className="card-header">
        <h2>Leave this year</h2>
        {data && <span className="muted small">1 Jan – 31 Dec {data.asOfDate?.slice(0, 4)}</span>}
      </div>
      {loading || !data ? (
        <Skeleton rows={3} />
      ) : (
        <>
          <div className="stats" style={{ marginBottom: 12 }}>
            <div className="card stat" style={{ boxShadow: 'none' }}>
              <span className="stat-label">Approved days</span>
              <span className="stat-value" style={{ fontSize: '1.5rem' }}>
                {formatMoney(data.totals.approvedDaysThisYear)}
              </span>
              <span className="stat-hint">{data.totals.approvedRequestsThisYear} approved requests</span>
            </div>
            <div className="card stat" style={{ boxShadow: 'none' }}>
              <span className="stat-label">Pending days</span>
              <span className="stat-value" style={{ fontSize: '1.5rem' }}>
                {formatMoney(data.totals.pendingDaysThisYear)}
              </span>
              <span className="stat-hint">{data.totals.pendingRequests} awaiting review</span>
            </div>
            <div className="card stat" style={{ boxShadow: 'none' }}>
              <span className="stat-label">Approved all time</span>
              <span className="stat-value" style={{ fontSize: '1.5rem' }}>
                {formatMoney(data.totals.approvedDaysAllTime)}
              </span>
              <span className="stat-hint">{data.totals.rejectedRequests} rejected · {data.totals.cancelledRequests} cancelled</span>
            </div>
          </div>

          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">Type</th>
                  <th scope="col" className="right">Approved (days)</th>
                  <th scope="col" className="right">Pending (days)</th>
                  <th scope="col" className="right">Requests</th>
                </tr>
              </thead>
              <tbody>
                {data.byType.map((t) => (
                  <tr key={t.type}>
                    <td data-label="Type">{titleCase(t.type)}</td>
                    <td data-label="Approved (days)" className="right">{formatMoney(t.approvedDaysThisYear)}</td>
                    <td data-label="Pending (days)" className="right">{formatMoney(t.pendingDaysThisYear)}</td>
                    <td data-label="Requests" className="right">{t.approvedRequestsThisYear}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  )
}

/** Decisions already taken on this employee's requests (approval history). */
function ApprovalHistory() {
  const fetcher = useCallback(() => employeeLeaveApi.balance(), [])
  const { data, loading, error, reload } = useFetch(fetcher)

  return (
    <section className="card">
      <h2>Approval history</h2>
      {loading ? (
        <Skeleton rows={4} />
      ) : error ? (
        <ErrorState title="History unavailable" message={error} onRetry={reload} />
      ) : data.history.length === 0 ? (
        <EmptyState icon="calendar" title="No decisions yet" hint="Once your administrator reviews a request it appears here." />
      ) : (
        <ul className="timeline">
          {data.history.slice(0, 12).map((h) => (
            <li key={h.leaveId} className={`is-${h.status.toLowerCase()}`}>
              <div className="timeline-title">
                {titleCase(h.type)} · {formatMoney(h.numberOfDays)} day(s)
                <StatusBadge value={h.status} />
              </div>
              <div className="timeline-meta">
                {formatDate(h.startDate)}
                {h.endDate !== h.startDate && ` – ${formatDate(h.endDate)}`}
                {h.reviewedAt && ` · reviewed ${formatDateTime(h.reviewedAt)}`}
                {h.adminName && ` by ${h.adminName}`}
              </div>
              {h.adminRemarks && <div className="small">“{h.adminRemarks}”</div>}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
