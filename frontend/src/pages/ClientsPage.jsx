import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { clientApi } from '../api/adminApi'
import { useFetch } from '../hooks/useFetch'
import { useToast } from '../hooks/useToast'
import PageHeader from '../components/common/PageHeader'
import StatusBadge from '../components/common/StatusBadge'
import DataTable from '../components/common/DataTable'
import ConfirmDialog from '../components/common/ConfirmDialog'
import { ErrorState, Spinner } from '../components/common/Feedback'
import { FilterSelect, SearchInput, Toolbar } from '../components/common/Toolbar'
import Icon from '../components/common/Icon'
import ClientFormModal from '../components/clients/ClientFormModal'
import { formatMoney, formatNumber, matchesQuery } from '../utils/format'

/** Money that may be unset: shows a dash instead of "0" when nothing was entered. */
const money = (value) => (value == null ? '—' : formatMoney(value))
const percent = (value) => (value == null ? '—' : `${formatMoney(value)}%`)

export default function ClientsPage() {
  const toast = useToast()
  const { data, loading, error, reload } = useFetch(clientApi.list)
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('')
  // Deep link from the dashboard's "Quick add client" action opens the dialog once.
  const [searchParams] = useSearchParams()
  const [form, setForm] = useState(() => (searchParams.get('new') === '1' ? {} : null)) // { client? }
  const [toggle, setToggle] = useState(null)
  const [removing, setRemoving] = useState(null)

  // Drop the query flag so a refresh does not reopen the dialog.
  useEffect(() => {
    if (searchParams.get('new') !== '1') return
    const next = new URLSearchParams(searchParams)
    next.delete('new')
    window.history.replaceState({}, '', `${window.location.pathname}${next.toString() ? `?${next}` : ''}`)
  }, [searchParams])

  const rows = useMemo(
    () =>
      (data ?? []).filter(
        (c) =>
          (!status || c.status === status) &&
          matchesQuery(c, query, [
            'clientCode',
            'clientName',
            'vendor',
            'bdmName',
            'position',
            'hrName',
            'contactNumber',
          ]),
      ),
    [data, query, status],
  )

  const columns = [
    {
      key: 'clientCode',
      label: 'Client ID',
      sortable: true,
      render: (c) => <span className="mono">{c.clientCode || '—'}</span>,
    },
    { key: 'clientName', label: 'Client', sortable: true, render: (c) => <strong>{c.clientName}</strong> },
    { key: 'vendor', label: 'Vendor', sortable: true, render: (c) => c.vendor || '—' },
    { key: 'bdmName', label: 'BDM', sortable: true, render: (c) => c.bdmName || '—' },
    { key: 'position', label: 'Position', sortable: true, render: (c) => c.position || '—' },
    {
      key: 'numberOfPositions',
      label: 'Positions',
      sortable: true,
      align: 'right',
      render: (c) => (c.numberOfPositions == null ? '—' : formatNumber(c.numberOfPositions)),
    },
    {
      key: 'salaryOffered',
      label: 'Salary offered',
      sortable: true,
      align: 'right',
      render: (c) => money(c.salaryOffered),
    },
    {
      key: 'percentagePerCandidate',
      label: 'Per candidate',
      sortable: true,
      align: 'right',
      render: (c) => percent(c.percentagePerCandidate),
    },
    { key: 'tax', label: 'Tax', sortable: true, align: 'right', render: (c) => percent(c.tax) },
    { key: 'status', label: 'Status', sortable: true, render: (c) => <StatusBadge value={c.status} /> },
    {
      key: 'actions',
      label: 'Actions',
      render: (c) => (
        <div className="row-actions">
          <button type="button" className="btn btn-sm" onClick={() => setForm({ client: c })}>
            Edit
          </button>
          <button
            type="button"
            className={c.status === 'ACTIVE' ? 'btn btn-sm btn-danger-ghost' : 'btn btn-sm btn-success'}
            onClick={() => setToggle(c)}
          >
            {c.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
          </button>
          <button type="button" className="btn btn-sm btn-danger-ghost" onClick={() => setRemoving(c)}>
            Delete
          </button>
        </div>
      ),
    },
  ]

  /** Expanded row: everything the client form holds, including contact details. */
  const detail = (c) => [
    ['Client ID', c.clientCode],
    ['Client', c.clientName],
    ['Vendor', c.vendor],
    ['BDM name', c.bdmName],
    ['Position', c.position],
    ['Contact number', c.contactNumber],
    ['Client HR name', c.hrName],
    ['HR contact details', c.hrContactDetails],
    ['Number of positions', c.numberOfPositions == null ? null : formatNumber(c.numberOfPositions)],
    ['Salary offered', c.salaryOffered == null ? null : formatMoney(c.salaryOffered)],
    ['Percentage per candidate', c.percentagePerCandidate == null ? null : percent(c.percentagePerCandidate)],
    ['Tax', c.tax == null ? null : percent(c.tax)],
    ['Profiles submitted', c.profilesSubmitted == null ? null : formatNumber(c.profilesSubmitted)],
    ['Interviews conducted', c.interviewsConducted == null ? null : formatNumber(c.interviewsConducted)],
    ['Selected candidates', c.selectedCandidates == null ? null : formatNumber(c.selectedCandidates)],
    ['Joining details', c.joiningDetails],
  ]

  async function toggleStatus() {
    await (toggle.status === 'ACTIVE' ? clientApi.deactivate(toggle.clientId) : clientApi.activate(toggle.clientId))
    toast.success(`${toggle.clientName} ${toggle.status === 'ACTIVE' ? 'deactivated' : 'activated'}`)
    setToggle(null)
    reload()
  }

  async function remove() {
    await clientApi.remove(removing.clientId)
    toast.success(`${removing.clientName} deleted`)
    setRemoving(null)
    reload()
  }

  return (
    <>
      <PageHeader
        title="Client management"
        subtitle="Every client your team recruits for. Employees file daily and weekly reports against an active client."
        actions={
          <button type="button" className="btn btn-primary" onClick={() => setForm({})}>
            <Icon name="plus" size={16} /> Add client
          </button>
        }
      />
      {loading ? (
        <Spinner />
      ) : error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : (
        <section className="card">
          <Toolbar>
            <SearchInput
              value={query}
              onChange={setQuery}
              placeholder="Search client ID, name, vendor, BDM, position…"
            />
            <FilterSelect
              label="Filter by status"
              value={status}
              onChange={setStatus}
              options={[
                { value: '', label: 'All statuses' },
                { value: 'ACTIVE', label: 'Active' },
                { value: 'INACTIVE', label: 'Inactive' },
              ]}
            />
            <span className="muted small push-right">
              {rows.length} of {(data ?? []).length} clients
            </span>
          </Toolbar>
          <DataTable
            columns={columns}
            rows={rows}
            rowKey="clientId"
            detail={detail}
            emptyTitle={data.length ? 'No clients match your filters' : 'No clients yet'}
            emptyHint={
              data.length
                ? undefined
                : 'Add your first client — it appears straight away in the employee report client lists.'
            }
            emptyAction={
              data.length ? undefined : (
                <button type="button" className="btn btn-primary" onClick={() => setForm({})}>
                  <Icon name="plus" size={16} /> Add client
                </button>
              )
            }
          />
        </section>
      )}
      {form && (
        <ClientFormModal
          client={form.client}
          onClose={() => setForm(null)}
          onSaved={(saved) => {
            toast.success(form.client ? `${saved.clientName} updated` : `${saved.clientName} added`)
            reload()
          }}
        />
      )}
      {toggle && (
        <ConfirmDialog
          title={toggle.status === 'ACTIVE' ? 'Deactivate client' : 'Activate client'}
          message={
            toggle.status === 'ACTIVE'
              ? `Deactivate ${toggle.clientName}? Employees won’t be able to pick it on new reports; existing reports keep it.`
              : `Activate ${toggle.clientName}? Employees will be able to pick it on new reports.`
          }
          confirmLabel={toggle.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
          danger={toggle.status === 'ACTIVE'}
          onConfirm={toggleStatus}
          onCancel={() => setToggle(null)}
        />
      )}
      {removing && (
        <ConfirmDialog
          title="Delete client"
          message={`Delete ${removing.clientName}? A client that already has reports cannot be deleted — deactivate it instead.`}
          confirmLabel="Delete"
          danger
          onConfirm={remove}
          onCancel={() => setRemoving(null)}
        />
      )}
    </>
  )
}
