import { useCallback, useState } from 'react'
import { administratorApi } from '../api/adminApi'
import { useFetch } from '../hooks/useFetch'
import { useToast } from '../hooks/useToast'
import PageHeader from '../components/common/PageHeader'
import DataTable from '../components/common/DataTable'
import FormModal from '../components/common/FormModal'
import StatusBadge from '../components/common/StatusBadge'
import { ErrorState } from '../components/common/Feedback'
import Icon from '../components/common/Icon'
import { formatDate } from '../utils/format'

/**
 * Administrators are never self-registered: an existing administrator creates
 * them here, and they sign in at /admin/login.
 */
const CREATE_FIELDS = [
  { name: 'adminName', label: 'Full name', required: true, maxLength: 100, half: true },
  { name: 'contactDetails', label: 'Phone', maxLength: 50, half: true, placeholder: '98765 43210' },
  { name: 'email', label: 'Email', type: 'email', required: true, maxLength: 150, hint: 'Used to sign in at /admin/login' },
  {
    name: 'password',
    label: 'Password',
    type: 'password',
    required: true,
    minLength: 8,
    autoComplete: 'new-password',
    hint: 'At least 8 characters — share it with the new administrator',
  },
]

const COLUMNS = [
  {
    key: 'adminName',
    label: 'Administrator',
    sortable: true,
    render: (a) => (
      <div>
        <div className="cell-title">{a.adminName}</div>
        <div className="cell-sub">{a.email}</div>
      </div>
    ),
  },
  { key: 'contactDetails', label: 'Phone', render: (a) => a.contactDetails || '—' },
  { key: 'status', label: 'Status', render: (a) => <StatusBadge value={a.status} /> },
  { key: 'createdAt', label: 'Added', render: (a) => formatDate(a.createdAt?.slice(0, 10)) },
]

export default function AdministratorsPage() {
  const toast = useToast()
  const [creating, setCreating] = useState(false)
  const fetcher = useCallback(() => administratorApi.list(), [])
  const list = useFetch(fetcher)

  const rows = Array.isArray(list.data) ? list.data : []

  async function create(values) {
    const created = await administratorApi.create({
      adminName: values.adminName.trim(),
      email: values.email.trim(),
      contactDetails: values.contactDetails.trim() || null,
      password: values.password,
    })
    toast.success(`${created.adminName} can now sign in at /admin/login`)
    setCreating(false)
    list.reload()
  }

  return (
    <>
      <PageHeader
        title="Administrators"
        subtitle="People who can manage employees, leave and reports."
        actions={
          <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>
            <Icon name="plus" size={16} /> Add administrator
          </button>
        }
      />

      <section className="card">
        {list.error ? (
          <ErrorState message={list.error} onRetry={list.reload} />
        ) : (
          <DataTable
            columns={COLUMNS}
            rows={rows}
            rowKey="adminId"
            emptyTitle="No administrators found"
            emptyHint="Add an administrator so more than one person can manage the team."
          />
        )}
      </section>

      {creating && (
        <FormModal
          title="Add administrator"
          fields={CREATE_FIELDS}
          submitLabel="Create administrator"
          onSubmit={create}
          onClose={() => setCreating(false)}
          hint={() => 'The new administrator signs in at /admin/login with this email and password.'}
        />
      )}
    </>
  )
}
