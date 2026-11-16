import { useCallback, useMemo, useState } from 'react'
import { adminEmployeeApi } from '../api/adminApi'
import { usePagedFetch } from '../hooks/useFetch'
import { useToast } from '../hooks/useToast'
import PageHeader from '../components/common/PageHeader'
import StatusBadge from '../components/common/StatusBadge'
import PagedTable from '../components/common/PagedTable'
import FormModal from '../components/common/FormModal'
import ConfirmDialog from '../components/common/ConfirmDialog'
import Modal from '../components/common/Modal'
import { EmptyState, ErrorState } from '../components/common/Feedback'
import { FilterSelect, SearchInput, Toolbar } from '../components/common/Toolbar'
import Icon from '../components/common/Icon'
import { formatDate, formatDateTime, titleCase } from '../utils/format'

const EDIT_FIELDS = [
  { name: 'employeeName', label: 'Full name', required: true, maxLength: 100 },
  { name: 'role', label: 'Job designation', required: true, maxLength: 50, half: true },
  { name: 'contact', label: 'Phone', maxLength: 50, half: true },
  { name: 'email', label: 'Email', type: 'email', maxLength: 150, hint: 'Changing this changes how the employee signs in' },
  // Optional profile dates: they feed the birthdays / anniversaries on the calendar.
  { name: 'dateOfBirth', label: 'Date of birth', type: 'date', half: true, hint: 'Shows as a birthday on the calendar' },
  { name: 'joiningDate', label: 'Joining date', type: 'date', half: true, hint: 'Drives work anniversaries' },
]

const STATUS_OPTIONS = [
  { value: '', label: 'All statuses' },
  ...['PENDING', 'APPROVED', 'INACTIVE', 'REJECTED'].map((s) => ({ value: s, label: titleCase(s) })),
]

// What each admin action means, by employee status (mirrors backend rules).
const ACTIONS = {
  PENDING: [
    { key: 'approve', label: 'Approve', confirm: 'Approve this employee? They will be able to sign in.', primary: true },
    { key: 'reject', label: 'Reject', danger: true, confirm: 'Reject this registration?' },
  ],
  APPROVED: [
    { key: 'deactivate', label: 'Deactivate', danger: true, confirm: 'Deactivate this employee? They will no longer be able to sign in.' },
  ],
  INACTIVE: [{ key: 'activate', label: 'Activate', confirm: 'Reactivate this employee?', primary: true }],
  REJECTED: [{ key: 'activate', label: 'Activate', confirm: 'Activate this previously rejected employee?', primary: true }],
}

/** Two-step onboarding state for a new employee account. */
function onboardingSteps(employee) {
  return [
    { label: 'Registered', done: true },
    { label: 'Approved by administrator', done: employee.accountStatus !== 'PENDING' && employee.accountStatus !== 'REJECTED' },
    { label: 'Can sign in', done: employee.accountStatus === 'APPROVED' },
  ]
}

export function OnboardingProgress({ employee }) {
  const steps = onboardingSteps(employee)
  const done = steps.filter((s) => s.done).length
  const pct = Math.round((done / steps.length) * 100)
  return (
    <div>
      <div className="progress" style={{ marginBottom: 8 }}>
        <div className="progress-track">
          <div
            className={`progress-fill${employee.accountStatus === 'APPROVED' ? ' tone-green' : ''}`}
            style={{ width: `${pct}%` }}
          />
        </div>
        <span className="progress-label">{pct}%</span>
      </div>
      <ul className="list list-tight">
        {steps.map((s) => (
          <li key={s.label}>
            <span style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <Icon name={s.done ? 'check-circle' : 'clock'} size={16} />
              {s.label}
            </span>
            <StatusBadge value={s.done ? 'APPROVED' : 'PENDING'} label={s.done ? 'Done' : 'Waiting'} />
          </li>
        ))}
      </ul>
    </div>
  )
}

export default function EmployeesPage() {
  const toast = useToast()
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('')
  const [editing, setEditing] = useState(null)
  const [viewing, setViewing] = useState(null)
  const [pending, setPending] = useState(null) // { employee, action }

  // Debouncing keeps typing from firing a request per keystroke.
  const params = useMemo(() => ({ search: query.trim() || undefined, status: status || undefined }), [query, status])
  const fetcher = useCallback((q) => adminEmployeeApi.page(q), [])
  const list = usePagedFetch(fetcher, params, { size: 10 })

  const rows = list.data?.content ?? []
  const hasFilters = Boolean(query.trim() || status)

  const columns = [
    {
      key: 'employeeName',
      label: 'Employee',
      sortKey: 'employeeName',
      render: (e) => (
        <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
          <span className="avatar" aria-hidden="true">
            {e.employeeName?.charAt(0)?.toUpperCase()}
          </span>
          <div>
            <div className="cell-title">{e.employeeName}</div>
            <div className="cell-sub">{e.email}</div>
          </div>
        </div>
      ),
    },
    { key: 'role', label: 'Designation', sortKey: 'role' },
    { key: 'contact', label: 'Phone', sortKey: null, render: (e) => e.contact || '—' },
    { key: 'accountStatus', label: 'Status', sortKey: 'accountStatus', render: (e) => <StatusBadge value={e.accountStatus} /> },
    { key: 'createdAt', label: 'Joined', sortKey: 'createdAt', render: (e) => formatDate(e.createdAt?.slice(0, 10)) },
    {
      key: 'actions',
      label: 'Actions',
      sortKey: null,
      render: (e) => (
        <div className="row-actions">
          <button type="button" className="btn btn-sm btn-ghost" onClick={() => setViewing(e)} aria-label={`View ${e.employeeName}`}>
            <Icon name="user" size={14} /> View
          </button>
          <button type="button" className="btn btn-sm" onClick={() => setEditing(e)}>
            Edit
          </button>
          {(ACTIONS[e.accountStatus] ?? []).map((a) => (
            <button
              key={a.key}
              type="button"
              className={
                a.danger ? 'btn btn-sm btn-danger-ghost' : a.primary ? 'btn btn-sm btn-success' : 'btn btn-sm'
              }
              onClick={() => setPending({ employee: e, action: a })}
            >
              {a.label}
            </button>
          ))}
        </div>
      ),
    },
  ]

  async function saveEdit(values) {
    await adminEmployeeApi.update(editing.employeeId, {
      employeeName: values.employeeName.trim(),
      role: values.role.trim(),
      contact: values.contact.trim() || null,
      email: values.email.trim() || null,
      // Empty date inputs submit "" — send null so the field is cleared, not rejected.
      dateOfBirth: values.dateOfBirth || null,
      joiningDate: values.joiningDate || null,
    })
    toast.success('Employee updated')
    setEditing(null)
    setViewing(null)
    list.reload()
  }

  async function runAction() {
    await adminEmployeeApi[pending.action.key](pending.employee.employeeId)
    toast.success(`${pending.employee.employeeName}: ${pending.action.label.toLowerCase()} done`)
    setPending(null)
    setViewing(null)
    list.reload()
  }

  return (
    <>
      <PageHeader
        title="Employees"
        subtitle="Approve registrations and manage your team."
        actions={
          hasFilters ? (
            <button
              type="button"
              className="btn"
              onClick={() => {
                setQuery('')
                setStatus('')
              }}
            >
              <Icon name="close" size={15} /> Clear filters
            </button>
          ) : null
        }
      />

      <section className="card">
        <Toolbar>
          <SearchInput value={query} onChange={setQuery} placeholder="Search name, email, designation…" />
          <FilterSelect label="Filter by status" value={status} onChange={setStatus} options={STATUS_OPTIONS} />
        </Toolbar>

        {list.error ? (
          <ErrorState message={list.error} onRetry={list.reload} />
        ) : (
          <PagedTable
            columns={columns}
            rows={rows}
            rowKey="employeeId"
            loading={list.loading && !list.data}
            sort={list.sort}
            onSort={list.toggleSort}
            page={list.page}
            totalPages={list.totalPages}
            totalElements={list.totalElements}
            onPageChange={list.setPage}
            emptyTitle={hasFilters ? 'No employees match your filters' : 'No employees yet'}
            emptyHint={
              hasFilters
                ? 'Try a different search term or status.'
                : 'Employees appear here after they register with your admin ID.'
            }
          />
        )}
      </section>

      {viewing && (
        <Modal
          title={viewing.employeeName}
          wide
          onClose={() => setViewing(null)}
          footer={
            <>
              <button type="button" className="btn" onClick={() => setEditing(viewing)}>
                Edit details
              </button>
              <button type="button" className="btn btn-primary" onClick={() => setViewing(null)}>
                Close
              </button>
            </>
          }
        >
          <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginBottom: 16 }}>
            <span className="avatar" style={{ width: 48, height: 48, fontSize: '1.1rem' }} aria-hidden="true">
              {viewing.employeeName?.charAt(0)?.toUpperCase()}
            </span>
            <div>
              <strong>{viewing.role}</strong>
              <div className="muted small">{viewing.email}</div>
            </div>
            <div className="spacer" />
            <StatusBadge value={viewing.accountStatus} />
          </div>

          <dl className="details" style={{ marginBottom: 20 }}>
            <div>
              <dt>Phone</dt>
              <dd>{viewing.contact || '—'}</dd>
            </div>
            <div>
              <dt>Employee ID</dt>
              <dd className="mono">#{viewing.employeeId}</dd>
            </div>
            <div>
              <dt>Registered</dt>
              <dd>{formatDateTime(viewing.createdAt)}</dd>
            </div>
            <div>
              <dt>Last updated</dt>
              <dd>{formatDateTime(viewing.updatedAt)}</dd>
            </div>
            <div>
              <dt>Managed by</dt>
              <dd>{viewing.adminName || '—'}</dd>
            </div>
          </dl>

          <h2>Onboarding</h2>
          <OnboardingProgress employee={viewing} />

          {(ACTIONS[viewing.accountStatus] ?? []).length > 0 && (
            <>
              <h2 style={{ marginTop: 20 }}>Account actions</h2>
              <div className="row-actions" style={{ justifyContent: 'flex-start' }}>
                {(ACTIONS[viewing.accountStatus] ?? []).map((a) => (
                  <button
                    key={a.key}
                    type="button"
                    className={a.danger ? 'btn btn-sm btn-danger-ghost' : 'btn btn-sm btn-primary'}
                    onClick={() => setPending({ employee: viewing, action: a })}
                  >
                    {a.label}
                  </button>
                ))}
              </div>
            </>
          )}
        </Modal>
      )}

      {editing && (
        <FormModal
          title="Edit employee"
          fields={EDIT_FIELDS}
          initialValues={editing}
          onSubmit={saveEdit}
          onClose={() => setEditing(null)}
        />
      )}

      {pending && (
        <ConfirmDialog
          title={`${pending.action.label} employee`}
          message={`${pending.action.confirm} (${pending.employee.employeeName})`}
          confirmLabel={pending.action.label}
          danger={Boolean(pending.action.danger)}
          onConfirm={runAction}
          onCancel={() => setPending(null)}
        />
      )}

      {!list.loading && !list.error && rows.length === 0 && !hasFilters && (
        <EmptyState
          icon="users"
          title="Invite your first employee"
          hint="Employees self-register with your admin ID, then appear here for approval."
        />
      )}
    </>
  )
}
