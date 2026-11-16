import { useMemo, useState } from 'react'
import { clientApi } from '../api/adminApi'
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
import { formatNumber, matchesQuery } from '../utils/format'

const FIELDS = [
  { name: 'clientName', label: 'Client name', required: true, maxLength: 150 },
  { name: 'hrName', label: 'HR contact name', maxLength: 100, half: true },
  { name: 'hrContactDetails', label: 'HR contact details', maxLength: 150, half: true },
  { name: 'profilesSubmitted', label: 'Profiles submitted', type: 'number', min: 0, half: true },
  { name: 'interviewsConducted', label: 'Interviews conducted', type: 'number', min: 0, half: true },
  { name: 'selectedCandidates', label: 'Selected candidates', type: 'number', min: 0, half: true },
  { name: 'joiningDetails', label: 'Joining details', type: 'textarea' },
]

const toPayload = (v) => ({
  clientName: v.clientName.trim(),
  hrName: v.hrName.trim() || null,
  hrContactDetails: v.hrContactDetails.trim() || null,
  profilesSubmitted: v.profilesSubmitted === '' ? 0 : Number(v.profilesSubmitted),
  interviewsConducted: v.interviewsConducted === '' ? 0 : Number(v.interviewsConducted),
  selectedCandidates: v.selectedCandidates === '' ? 0 : Number(v.selectedCandidates),
  joiningDetails: v.joiningDetails.trim() || null,
})

export default function ClientsPage() {
  const toast = useToast()
  const { data, loading, error, reload } = useFetch(clientApi.list)
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('')
  const [form, setForm] = useState(null) // { client? }
  const [toggle, setToggle] = useState(null)

  const rows = useMemo(
    () =>
      (data ?? []).filter(
        (c) =>
          (!status || c.status === status) && matchesQuery(c, query, ['clientName', 'hrName', 'hrContactDetails']),
      ),
    [data, query, status],
  )

  const columns = [
    { key: 'clientName', label: 'Client', sortable: true, render: (c) => <strong>{c.clientName}</strong> },
    { key: 'hrName', label: 'HR contact', sortable: true, render: (c) => c.hrName || '—' },
    { key: 'hrContactDetails', label: 'Contact details', render: (c) => c.hrContactDetails || '—' },
    { key: 'profilesSubmitted', label: 'Profiles', sortable: true, align: 'right', render: (c) => formatNumber(c.profilesSubmitted ?? 0) },
    { key: 'interviewsConducted', label: 'Interviews', sortable: true, align: 'right', render: (c) => formatNumber(c.interviewsConducted ?? 0) },
    { key: 'selectedCandidates', label: 'Selected', sortable: true, align: 'right', render: (c) => formatNumber(c.selectedCandidates ?? 0) },
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
        </div>
      ),
    },
  ]

  async function save(values) {
    if (form.client) {
      await clientApi.update(form.client.clientId, toPayload(values))
      toast.success('Client updated')
    } else {
      await clientApi.create(toPayload(values))
      toast.success('Client created')
    }
    setForm(null)
    reload()
  }

  async function toggleStatus() {
    await (toggle.status === 'ACTIVE' ? clientApi.deactivate(toggle.clientId) : clientApi.activate(toggle.clientId))
    toast.success(`${toggle.clientName} ${toggle.status === 'ACTIVE' ? 'deactivated' : 'activated'}`)
    setToggle(null)
    reload()
  }

  return (
    <>
      <PageHeader
        title="Clients"
        subtitle="Companies you recruit for. Employees can only file reports against active clients."
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
            <SearchInput value={query} onChange={setQuery} placeholder="Search clients…" />
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
          </Toolbar>
          <DataTable
            columns={columns}
            rows={rows}
            rowKey="clientId"
            emptyTitle={data.length ? 'No clients match your filters' : 'No clients yet'}
            emptyHint={data.length ? undefined : 'Add your first client to start tracking reports.'}
          />
        </section>
      )}
      {form && (
        <FormModal
          wide
          title={form.client ? 'Edit client' : 'Add client'}
          fields={FIELDS}
          initialValues={form.client ?? {}}
          submitLabel={form.client ? 'Save changes' : 'Create client'}
          onSubmit={save}
          onClose={() => setForm(null)}
        />
      )}
      {toggle && (
        <ConfirmDialog
          title={toggle.status === 'ACTIVE' ? 'Deactivate client' : 'Activate client'}
          message={
            toggle.status === 'ACTIVE'
              ? `Deactivate ${toggle.clientName}? Employees won’t be able to file new reports against it.`
              : `Activate ${toggle.clientName}? Employees will be able to file reports against it.`
          }
          confirmLabel={toggle.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
          danger={toggle.status === 'ACTIVE'}
          onConfirm={toggleStatus}
          onCancel={() => setToggle(null)}
        />
      )}
    </>
  )
}
