import { useCallback, useMemo, useState } from 'react'
import { administratorApi } from '../api/adminApi'
import { useAuth } from '../hooks/useAuth'
import { useFetch } from '../hooks/useFetch'
import { useToast } from '../hooks/useToast'
import PageHeader from '../components/common/PageHeader'
import DataTable from '../components/common/DataTable'
import FormModal from '../components/common/FormModal'
import ConfirmDialog from '../components/common/ConfirmDialog'
import Modal from '../components/common/Modal'
import StatusBadge from '../components/common/StatusBadge'
import ActionsMenu from '../components/common/ActionsMenu'
import { ErrorState, Spinner } from '../components/common/Feedback'
import Icon from '../components/common/Icon'
import { formatDate, formatDateTime } from '../utils/format'

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

const EDIT_FIELDS = [
  { name: 'adminName', label: 'Full name', required: true, maxLength: 100, half: true },
  { name: 'contactDetails', label: 'Phone', maxLength: 50, half: true, placeholder: '98765 43210' },
  { name: 'email', label: 'Email', type: 'email', required: true, maxLength: 150, hint: 'Used to sign in at /admin/login' },
  {
    name: 'password',
    label: 'New password',
    type: 'password',
    minLength: 8,
    autoComplete: 'new-password',
    hint: 'Leave empty to keep the current password. At least 8 characters when set.',
  },
]

/** Matches the backend rules so a bad value never needs a round trip. */
function validateEdit(values) {
  const errors = {}
  const name = String(values.adminName ?? '').trim()
  const email = String(values.email ?? '').trim()
  const phone = String(values.contactDetails ?? '').trim()
  const password = String(values.password ?? '')

  if (!name) errors.adminName = 'Full name is required'
  if (!/^\S+@\S+\.\S+$/.test(email)) errors.email = 'Enter a valid email'
  if (phone && !/^[0-9+()\-\s]{6,50}$/.test(phone)) {
    errors.contactDetails = 'Use at least 6 digits; + ( ) - and spaces are allowed'
  }
  if (password && password.length < 8) errors.password = 'Password must be at least 8 characters'
  if (password.length > 72) errors.password = 'Password must be at most 72 characters'

  return errors
}

const trimOrNull = (value) => {
  const trimmed = String(value ?? '').trim()
  return trimmed === '' ? null : trimmed
}

export default function AdministratorsPage() {
  const toast = useToast()
  const { user } = useAuth()
  const [creating, setCreating] = useState(false)
  const [viewing, setViewing] = useState(null)
  const [editing, setEditing] = useState(null)
  const [toggling, setToggling] = useState(null)
  const [removing, setRemoving] = useState(null)

  const fetcher = useCallback(() => administratorApi.list(), [])
  const list = useFetch(fetcher)

  const rows = useMemo(() => (Array.isArray(list.data) ? list.data : []), [list.data])
  const activeCount = rows.filter((row) => row.status === 'ACTIVE').length
  const isSelf = (admin) => admin.adminId === user?.userId

  async function create(values) {
    const created = await administratorApi.create({
      adminName: values.adminName.trim(),
      email: values.email.trim(),
      contactDetails: trimOrNull(values.contactDetails),
      password: values.password,
    })
    toast.success(`${created.adminName} can now sign in at /admin/login`)
    setCreating(false)
    list.reload()
  }

  async function saveEdit(values) {
    const updated = await administratorApi.update(editing.adminId, {
      adminName: values.adminName.trim(),
      email: values.email.trim(),
      contactDetails: trimOrNull(values.contactDetails),
      password: String(values.password ?? '').trim() || null,
    })
    toast.success(`${updated.adminName} updated`)
    setEditing(null)
    list.reload()
  }

  async function toggleStatus() {
    const activating = toggling.status !== 'ACTIVE'
    const updated = activating
      ? await administratorApi.activate(toggling.adminId)
      : await administratorApi.deactivate(toggling.adminId)
    toast.success(`${updated.adminName} ${activating ? 'reactivated' : 'deactivated'}`)
    setToggling(null)
    list.reload()
  }

  async function remove() {
    await administratorApi.remove(removing.adminId)
    toast.success(`${removing.adminName} deleted`)
    setRemoving(null)
    list.reload()
  }

  /** Why an action is unavailable, or null when it is allowed. Backend enforces the same rules. */
  function blockedReason(admin, action) {
    if (isSelf(admin)) return `You cannot ${action} your own account`
    if (
      admin.status === 'ACTIVE' &&
      activeCount <= 1 &&
      (action === 'deactivate' || action === 'delete')
    ) {
      return 'This is the last active administrator'
    }
    return null
  }

  const columns = [
    {
      key: 'adminName',
      label: 'Administrator',
      sortable: true,
      render: (a) => (
        <div>
          <div className="cell-title">
            {a.adminName}
            {isSelf(a) && <span className="chip chip-you">You</span>}
          </div>
          <div className="cell-sub">{a.email}</div>
        </div>
      ),
    },
    { key: 'contactDetails', label: 'Phone', render: (a) => a.contactDetails || '—' },
    { key: 'status', label: 'Status', sortable: true, render: (a) => <StatusBadge value={a.status} /> },
    { key: 'createdAt', label: 'Added', sortable: true, render: (a) => formatDate(a.createdAt?.slice(0, 10)) },
    {
      key: 'actions',
      label: 'Actions',
      align: 'right',
      render: (a) => {
        const activate = a.status !== 'ACTIVE'
        const statusAction = activate ? 'reactivate' : 'deactivate'
        const statusBlocked = blockedReason(a, statusAction)
        const deleteBlocked = blockedReason(a, 'delete')

        return (
          <div className="row-actions">
            <ActionsMenu
              label={`Actions for ${a.adminName}`}
              items={[
                { key: 'view', label: 'View', icon: 'user', onSelect: () => setViewing(a) },
                { key: 'edit', label: 'Edit', icon: 'edit', onSelect: () => setEditing(a) },
                {
                  key: 'status',
                  label: activate ? 'Reactivate' : 'Deactivate',
                  icon: activate ? 'check' : 'shield',
                  disabled: Boolean(statusBlocked),
                  hint: statusBlocked,
                  onSelect: () => setToggling(a),
                },
                {
                  key: 'delete',
                  label: 'Delete',
                  icon: 'trash',
                  tone: 'danger',
                  disabled: Boolean(deleteBlocked),
                  hint: deleteBlocked,
                  onSelect: () => setRemoving(a),
                },
              ]}
            />
          </div>
        )
      },
    },
  ]

  return (
    <>
      <PageHeader
        title="Administrators"
        subtitle="Administrators share this company's employees, clients, leave, reports and dashboards."
        actions={
          <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>
            <Icon name="plus" size={16} /> Add administrator
          </button>
        }
      />

      <section className="card">
        {list.error ? (
          <ErrorState message={list.error} onRetry={list.reload} />
        ) : list.loading ? (
          <Spinner />
        ) : (
          <DataTable
            columns={columns}
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
          hint={() =>
            'The new administrator joins your company: they sign in at /admin/login and see the same employees, clients, leave, reports and dashboards you do.'
          }
        />
      )}

      {viewing && <ViewAdministrator admin={viewing} isSelf={isSelf(viewing)} onClose={() => setViewing(null)} />}

      {editing && (
        <FormModal
          title={`Edit ${editing.adminName}`}
          fields={EDIT_FIELDS}
          initialValues={{
            adminName: editing.adminName,
            contactDetails: editing.contactDetails ?? '',
            email: editing.email,
            password: '',
          }}
          validate={validateEdit}
          submitLabel="Save changes"
          onSubmit={saveEdit}
          onClose={() => setEditing(null)}
          hint={() =>
            editing.adminId === user?.userId
              ? 'You are editing your own account. Changing the email or password affects your next sign-in.'
              : null
          }
        />
      )}

      {toggling && (
        <ConfirmDialog
          title={toggling.status === 'ACTIVE' ? 'Deactivate administrator' : 'Reactivate administrator'}
          message={
            toggling.status === 'ACTIVE'
              ? `${toggling.adminName} (${toggling.email}) will lose access immediately — any signed-in session stops working on its next request. Their records stay untouched.`
              : `${toggling.adminName} (${toggling.email}) will be able to sign in and manage the team again.`
          }
          confirmLabel={toggling.status === 'ACTIVE' ? 'Deactivate' : 'Reactivate'}
          danger={toggling.status === 'ACTIVE'}
          onConfirm={toggleStatus}
          onCancel={() => setToggling(null)}
        />
      )}

      {removing && (
        <ConfirmDialog
          title="Delete administrator"
          message={`Delete ${removing.adminName} (${removing.email})? This permanently removes the account. Deletion is refused while the administrator still owns employees, clients, holidays, notes or reviewed leave requests — deactivate them instead to keep that history.`}
          confirmLabel="Delete administrator"
          danger
          onConfirm={remove}
          onCancel={() => setRemoving(null)}
        />
      )}
    </>
  )
}

/** Read-only details. The API never returns a password hash, and none is echoed here. */
function ViewAdministrator({ admin, isSelf, onClose }) {
  const rows = [
    ['Full name', admin.adminName],
    ['Email', admin.email],
    ['Phone', admin.contactDetails],
    ['Access level', admin.roleAccessLevel],
    ['Company', admin.companyName],
    ['Added', formatDateTime(admin.createdAt)],
    ['Last updated', admin.updatedAt ? formatDateTime(admin.updatedAt) : null],
  ]

  return (
    <Modal
      title="Administrator details"
      onClose={onClose}
      footer={
        <button type="button" className="btn btn-primary" onClick={onClose}>
          Close
        </button>
      }
    >
      <div className="admin-view-head">
        <div>
          <strong>{admin.adminName}</strong>
          <div className="muted small">{admin.email}</div>
        </div>
        <div className="admin-view-badges">
          {isSelf && <span className="chip chip-you">You</span>}
          <StatusBadge value={admin.status} />
        </div>
      </div>

      <dl className="details">
        {rows
          .filter(([, value]) => value != null && value !== '')
          .map(([label, value]) => (
            <div key={label}>
              <dt>{label}</dt>
              <dd>{value}</dd>
            </div>
          ))}
      </dl>

      <p className="muted small">Passwords are never shown or returned by the API.</p>
    </Modal>
  )
}
