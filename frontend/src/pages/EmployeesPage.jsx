import { useCallback, useMemo, useState } from 'react'
import { adminEmployeeApi } from '../api/adminApi'
import { usePagedFetch } from '../hooks/useFetch'
import { useToast } from '../hooks/useToast'
import PageHeader from '../components/common/PageHeader'
import StatusBadge from '../components/common/StatusBadge'
import PagedTable from '../components/common/PagedTable'
import ConfirmDialog from '../components/common/ConfirmDialog'
import Modal from '../components/common/Modal'
import EmployeeFormModal from '../components/employees/EmployeeFormModal'
import { EmptyState, ErrorState } from '../components/common/Feedback'
import { FilterSelect, SearchInput, Toolbar } from '../components/common/Toolbar'
import Icon from '../components/common/Icon'
import { formatDateTime, formatMoney, titleCase } from '../utils/format'

const STATUS_OPTIONS = [
  { value: '', label: 'All statuses' },
  ...['APPROVED', 'INACTIVE'].map((s) => ({ value: s, label: titleCase(s) })),
]

function accountActions(status) {
  switch (status) {
    case 'APPROVED':
      return [
        {
          key: 'deactivate',
          label: 'Deactivate',
          danger: true,
          confirm: 'Deactivate? This employee will no longer be able to sign in.',
        },
      ]
    case 'INACTIVE':
      return [{ key: 'activate', label: 'Activate', primary: true, confirm: 'Reactivate this employee?' }]
    default:
      return []
  }
}

export default function EmployeesPage() {
  const toast = useToast()
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('')
  const [forming, setForming] = useState(null) // { mode: 'create' } | { mode: 'edit', employee }
  const [viewing, setViewing] = useState(null)
  const [pending, setPending] = useState(null) // { employee, key }

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
    {
      key: 'employeeCode',
      label: 'Employee ID',
      sortKey: 'employeeCode',
      render: (e) => <span className="mono">{e.employeeCode || `#${e.employeeId}`}</span>,
    },
    { key: 'department', label: 'Department', sortKey: 'department', render: (e) => e.department || '—' },
    { key: 'role', label: 'Designation', sortKey: 'role' },
    { key: 'salary', label: 'Salary', sortKey: null, render: (e) => formatMoney(e.salary) },
    { key: 'accountStatus', label: 'Status', sortKey: 'accountStatus', render: (e) => <StatusBadge value={e.accountStatus} /> },
    {
      key: 'actions',
      label: 'Actions',
      sortKey: null,
      render: (e) => (
        <div className="row-actions">
          <button type="button" className="btn btn-sm btn-ghost" onClick={() => setViewing(e)} aria-label={`View ${e.employeeName}`}>
            <Icon name="user" size={14} /> View
          </button>
          <button type="button" className="btn btn-sm" onClick={() => setForming({ mode: 'edit', employee: e })}>
            Edit
          </button>
          {accountActions(e.accountStatus).map((a) => (
            <button
              key={a.key}
              type="button"
              className={a.danger ? 'btn btn-sm btn-danger-ghost' : a.primary ? 'btn btn-sm btn-success' : 'btn btn-sm'}
              onClick={() => setPending({ employee: e, action: a })}
            >
              {a.label}
            </button>
          ))}
          <button
            type="button"
            className="btn btn-sm btn-danger-ghost"
            onClick={() => setPending({ employee: e, action: { key: 'remove', label: 'Delete', danger: true } })}
          >
            Delete
          </button>
        </div>
      ),
    },
  ]

  async function runAction() {
    const { employee, action } = pending

    if (action.key === 'remove') {
      await adminEmployeeApi.remove(employee.employeeId)
      toast.success(`${employee.employeeName} deleted`)
    } else {
      await adminEmployeeApi[action.key](employee.employeeId)
      toast.success(`${employee.employeeName}: ${action.label.toLowerCase()} done`)
    }

    setPending(null)
    setViewing(null)
    list.reload()
  }

  return (
    <>
      <PageHeader
        title="Employees"
        subtitle="Create accounts, manage your team and reset passwords."
        actions={
          <>
            {hasFilters && (
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
            )}
            <button type="button" className="btn btn-primary" onClick={() => setForming({ mode: 'create' })}>
              <Icon name="plus" size={16} /> Add employee
            </button>
          </>
        }
      />

      <section className="card">
        <Toolbar>
          <SearchInput value={query} onChange={setQuery} placeholder="Search name, email, ID, department…" />
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
            emptyHint={hasFilters ? 'Try a different search term or status.' : 'Use “Add employee” to create the first account.'}
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
              <button type="button" className="btn" onClick={() => setForming({ mode: 'edit', employee: viewing })}>
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
              <dt>Employee ID</dt>
              <dd className="mono">{viewing.employeeCode || `#${viewing.employeeId}`}</dd>
            </div>
            <div>
              <dt>Phone</dt>
              <dd>{viewing.contact || '—'}</dd>
            </div>
            <div>
              <dt>Department</dt>
              <dd>{viewing.department || '—'}</dd>
            </div>
            <div>
              <dt>Salary</dt>
              <dd>{formatMoney(viewing.salary)}</dd>
            </div>
            <div>
              <dt>Created</dt>
              <dd>{formatDateTime(viewing.createdAt)}</dd>
            </div>
            <div>
              <dt>Last updated</dt>
              <dd>{formatDateTime(viewing.updatedAt)}</dd>
            </div>
          </dl>

          {accountActions(viewing.accountStatus).length > 0 && (
            <div className="row-actions" style={{ justifyContent: 'flex-start' }}>
              {accountActions(viewing.accountStatus).map((a) => (
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
          )}
        </Modal>
      )}

      {forming && (
        <EmployeeFormModal
          employee={forming.mode === 'edit' ? forming.employee : null}
          onClose={() => setForming(null)}
          onSaved={(_saved, verb) => {
            toast.success(verb === 'created' ? 'Employee created — they can sign in now' : 'Employee updated')
            setViewing(null)
            list.reload()
          }}
        />
      )}

      {pending && (
        <ConfirmDialog
          title={`${pending.action.label} employee`}
          message={
            pending.action.key === 'remove'
              ? `Delete ${pending.employee.employeeName} permanently? Their attendance, leave, reports, performance and feedback records are removed along with the account.`
              : `${pending.employee.employeeName}: ${pending.action.confirm}`
          }
          confirmLabel={pending.action.label}
          danger
          onConfirm={runAction}
          onCancel={() => setPending(null)}
        />
      )}

      {!list.loading && !list.error && rows.length === 0 && !hasFilters && (
        <EmptyState
          icon="users"
          title="Build your team"
          hint="Add the first employee — you set their employee ID, email, phone, department, role, salary and password."
        />
      )}
    </>
  )
}