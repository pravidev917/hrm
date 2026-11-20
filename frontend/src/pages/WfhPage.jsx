import { useCallback, useMemo, useState } from 'react'
import { adminWfhApi, employeeWfhApi } from '../api/wfhApi'
import { useAuth } from '../hooks/useAuth'
import { useFetch } from '../hooks/useFetch'
import { useToast } from '../hooks/useToast'
import PageHeader from '../components/common/PageHeader'
import StatusBadge from '../components/common/StatusBadge'
import DataTable from '../components/common/DataTable'
import FormModal from '../components/common/FormModal'
import ConfirmDialog from '../components/common/ConfirmDialog'
import { ErrorState, Spinner } from '../components/common/Feedback'
import { FilterSelect, SearchInput, Toolbar } from '../components/common/Toolbar'
import Icon from '../components/common/Icon'
import { formatDate, formatDateTime, matchesQuery, titleCase, todayISO } from '../utils/format'

const STATUS_OPTIONS = [
  { value: '', label: 'All statuses' },
  ...['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'].map((s) => ({ value: s, label: titleCase(s) })),
]

const REQUEST_FIELDS = [
  { name: 'startDate', label: 'Start date', type: 'date', min: todayISO(), required: true, half: true },
  { name: 'endDate', label: 'End date', type: 'date', min: todayISO(), required: true, half: true },
  { name: 'reason', label: 'Reason', type: 'textarea', required: true, maxLength: 2000 },
]

function validateRequest(values) {
  const errors = {}
  if (values.startDate && values.endDate && values.endDate < values.startDate) {
    errors.endDate = 'End date cannot be before start date'
  }
  return errors
}

export default function WfhPage() {
  const { isAdmin } = useAuth()
  const toast = useToast()
  const fetcher = useCallback(() => (isAdmin ? adminWfhApi.list() : employeeWfhApi.list()), [isAdmin])
  const { data, loading, error, reload } = useFetch(fetcher)

  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('')
  const [form, setForm] = useState(false)
  const [review, setReview] = useState(null)
  const [revoke, setRevoke] = useState(null)
  const [cancelling, setCancelling] = useState(null)

  const rows = useMemo(
    () =>
      (data ?? [])
        .filter(
          (w) =>
            (!status || w.status === status) &&
            matchesQuery(w, query, ['employeeName', 'reason', 'adminComment']),
        )
        .sort((a, b) => b.startDate.localeCompare(a.startDate)),
    [data, query, status],
  )

  const counts = useMemo(() => {
    const base = { PENDING: 0, APPROVED: 0, REJECTED: 0, CANCELLED: 0 }
    for (const w of data ?? []) if (base[w.status] != null) base[w.status] += 1
    return base
  }, [data])

  const columns = [
    ...(isAdmin
      ? [{ key: 'employeeName', label: 'Employee', sortable: true, render: (w) => <strong>{w.employeeName}</strong> }]
      : []),
    {
      key: 'startDate',
      label: 'Dates',
      sortable: true,
      render: (w) => (
        <>
          {formatDate(w.startDate)}
          {w.endDate !== w.startDate && <> – {formatDate(w.endDate)}</>}
          {w.coversToday && <div className="muted small">Covers today</div>}
        </>
      ),
    },
    { key: 'reason', label: 'Reason', render: (w) => <span className="clamp" title={w.reason}>{w.reason}</span> },
    { key: 'status', label: 'Status', sortable: true, render: (w) => <StatusBadge value={w.status} /> },
    {
      key: 'review',
      label: 'Decision',
      render: (w) =>
        w.reviewedAt || w.adminComment ? (
          <div>
            {w.reviewedByName && <div className="small">{w.reviewedByName}</div>}
            {w.adminComment && (
              <span className="clamp" title={w.adminComment}>
                {w.adminComment}
              </span>
            )}
            {w.reviewedAt && <div className="muted small">{formatDateTime(w.reviewedAt)}</div>}
          </div>
        ) : (
          '—'
        ),
    },
    {
      key: 'actions',
      label: 'Actions',
      render: (w) => {
        if (isAdmin) {
          if (w.status === 'PENDING') {
            return (
              <button type="button" className="btn btn-sm btn-primary" onClick={() => setReview(w)}>
                Review
              </button>
            )
          }
          if (w.status === 'APPROVED') {
            return (
              <button type="button" className="btn btn-sm btn-danger-ghost" onClick={() => setRevoke(w)}>
                Revoke
              </button>
            )
          }
          return null
        }
        if (w.status !== 'PENDING') return null
        return (
          <button type="button" className="btn btn-sm btn-danger-ghost" onClick={() => setCancelling(w)}>
            Cancel
          </button>
        )
      },
    },
  ]

  async function submitRequest(values) {
    await employeeWfhApi.apply({
      startDate: values.startDate,
      endDate: values.endDate,
      reason: values.reason.trim(),
    })
    toast.success('Work-from-home request submitted')
    setForm(false)
    reload()
  }

  async function submitReview(values) {
    await adminWfhApi.review(review.wfhId, {
      status: values.status,
      comment: values.comment.trim() || null,
    })
    toast.success(`WFH request ${values.status.toLowerCase()}`)
    setReview(null)
    reload()
  }

  async function submitRevoke(values) {
    await adminWfhApi.revoke(revoke.wfhId, values.comment.trim() || null)
    toast.success('WFH approval revoked')
    setRevoke(null)
    reload()
  }

  async function cancel() {
    await employeeWfhApi.cancel(cancelling.wfhId)
    toast.success('WFH request cancelled')
    setCancelling(null)
    reload()
  }

  return (
    <>
      <PageHeader
        title={isAdmin ? 'Work from home' : 'My work from home'}
        subtitle={
          isAdmin
            ? 'Review work-from-home requests from your team. Every decision shows who made it and when.'
            : 'Request WFH days and follow every decision. Approved days let you mark present without sharing an office location.'
        }
        actions={
          !isAdmin && (
            <button type="button" className="btn btn-primary" onClick={() => setForm(true)}>
              <Icon name="plus" size={16} /> Request WFH
            </button>
          )
        }
      />

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
            placeholder={isAdmin ? 'Search employee, reason…' : 'Search reason…'}
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
            rowKey="wfhId"
            emptyTitle={data.length ? 'No requests match your filters' : 'No work-from-home requests yet'}
            emptyHint={!isAdmin && !data.length ? 'Use “Request WFH” to submit your first request.' : undefined}
          />
        )}
      </section>

      {form && (
        <FormModal
          title="Request work from home"
          fields={REQUEST_FIELDS}
          initialValues={{}}
          validate={validateRequest}
          submitLabel="Submit request"
          onSubmit={submitRequest}
          onClose={() => setForm(false)}
          intro={
            <div className="alert alert-info">
              <Icon name="info" size={16} />
              <span>
                An administrator must approve the request. Pending or rejected requests do not bypass office location
                verification. Your home coordinates are never requested.
              </span>
            </div>
          }
        />
      )}

      {review && (
        <FormModal
          title={`Review WFH · ${review.employeeName}`}
          fields={[
            {
              name: 'status',
              label: 'Decision',
              type: 'select',
              required: true,
              options: [
                { value: 'APPROVED', label: 'Approve' },
                { value: 'REJECTED', label: 'Reject' },
              ],
            },
            { name: 'comment', label: 'Comment (optional)', type: 'textarea', maxLength: 2000 },
          ]}
          initialValues={{ status: 'APPROVED', comment: '' }}
          submitLabel="Submit decision"
          onSubmit={submitReview}
          onClose={() => setReview(null)}
          intro={
            <div className="alert alert-info">
              <Icon name="home" size={16} />
              <span>
                {review.employeeName} · {formatDate(review.startDate)}
                {review.endDate !== review.startDate && ` – ${formatDate(review.endDate)}`}
                {review.reason && <div className="small">“{review.reason}”</div>}
              </span>
            </div>
          }
        />
      )}

      {revoke && (
        <FormModal
          title={`Revoke WFH approval · ${revoke.employeeName}`}
          fields={[{ name: 'comment', label: 'Reason (optional)', type: 'textarea', maxLength: 2000 }]}
          initialValues={{ comment: '' }}
          submitLabel="Revoke approval"
          onSubmit={submitRevoke}
          onClose={() => setRevoke(null)}
          intro={
            <div className="alert alert-warning">
              <Icon name="alert" size={16} />
              <span>
                Attendance already recorded as WFH stays in history. Revoking only stops the approval from being used
                for future attendance.
              </span>
            </div>
          }
        />
      )}

      {cancelling && (
        <ConfirmDialog
          title="Cancel WFH request"
          message={`Cancel your work-from-home request from ${formatDate(cancelling.startDate)} to ${formatDate(
            cancelling.endDate,
          )}?`}
          confirmLabel="Cancel request"
          danger
          onConfirm={cancel}
          onCancel={() => setCancelling(null)}
        />
      )}
    </>
  )
}
