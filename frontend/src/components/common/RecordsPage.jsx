import { useCallback, useMemo, useState } from 'react'
import { adminEmployeeApi } from '../../api/adminApi'
import { useAuth } from '../../hooks/useAuth'
import { useFetch } from '../../hooks/useFetch'
import { useToast } from '../../hooks/useToast'
import PageHeader from './PageHeader'
import DataTable from './DataTable'
import FormModal from './FormModal'
import ConfirmDialog from './ConfirmDialog'
import { ErrorState } from './Feedback'
import Skeleton from './Skeleton'
import { SearchInput, Toolbar } from './Toolbar'
import ExportButtons from './ExportButtons'
import { matchesQuery } from '../../utils/format'

/**
 * Shared CRUD page for records that employees keep for themselves and admins keep for an employee
 * (feedback, follow-ups, performance). All data and permissions come from the backend.
 *
 * config: { title, subtitle, noun, idKey, api: { employee, admin }, fields, columns, searchKeys, validate?, toPayload }
 */
export default function RecordsPage({ config, hideHeader = false }) {
  const { isAdmin } = useAuth()
  const toast = useToast()
  const api = isAdmin ? config.api.admin : config.api.employee

  // Admins also need the employee list to pick who a new record is for.
  const fetcher = useCallback(async () => {
    if (!isAdmin) return { records: await api.list(), employees: [] }
    const [records, employees] = await Promise.all([api.list(), adminEmployeeApi.list()])
    return { records, employees: employees.filter((e) => e.accountStatus === 'APPROVED') }
  }, [api, isAdmin])

  const { data, loading, error, reload } = useFetch(fetcher)
  const [query, setQuery] = useState('')
  const [form, setForm] = useState(null)
  const [removing, setRemoving] = useState(null)

  const records = data?.records
  // What the table shows; an export re-runs the same search on the server.
  const rows = useMemo(
    () => (records ?? []).filter((r) => matchesQuery(r, query, [...config.searchKeys, ...(isAdmin ? ['employeeName'] : [])])),
    [records, query, config.searchKeys, isAdmin],
  )

  const fields = useMemo(() => {
    if (!isAdmin) return config.fields
    const employeeField = {
      name: 'employeeId',
      label: 'Employee',
      type: 'select',
      required: true,
      options: (data?.employees ?? []).map((e) => ({ value: String(e.employeeId), label: e.employeeName })),
    }
    return [employeeField, ...config.fields]
  }, [isAdmin, config.fields, data?.employees])

  const columns = [
    ...(isAdmin ? [{ key: 'employeeName', label: 'Employee', sortable: true, render: (r) => <strong>{r.employeeName}</strong> }] : []),
    ...config.columns,
    {
      key: 'actions',
      label: 'Actions',
      render: (r) => (
        <div className="row-actions">
          <button type="button" className="btn btn-sm" onClick={() => setForm({ record: r })}>Edit</button>
          <button type="button" className="btn btn-sm btn-danger-ghost" onClick={() => setRemoving(r)}>Delete</button>
        </div>
      ),
    },
  ]

  async function save(values) {
    const body = config.toPayload(values)
    if (form.record) {
      await api.update(form.record[config.idKey], body)
      toast.success(`${config.noun} updated`)
    } else {
      await (isAdmin ? api.create(body, values.employeeId) : api.create(body))
      toast.success(`${config.noun} added`)
    }
    setForm(null)
    reload()
  }

  async function remove() {
    await api.remove(removing[config.idKey])
    toast.success(`${config.noun} deleted`)
    setRemoving(null)
    reload()
  }

  // The employee cannot be changed on an existing record (the update endpoint only edits the content).
  const editFields = form?.record ? fields.filter((f) => f.name !== 'employeeId') : fields

  return (
    <>
      {!hideHeader && <PageHeader
        title={config.title}
        subtitle={config.subtitle}
        actions={<button type="button" className="btn btn-primary" onClick={() => setForm({})}>Add {config.noun.toLowerCase()}</button>}
      />}
      {loading ? (
        <section className="card">
          <Skeleton rows={6} />
        </section>
      ) : error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : (
        <section className="card">
          <Toolbar>
            <SearchInput value={query} onChange={setQuery} placeholder="Search…" />
            {isAdmin && config.exportEndpoint && (
              <ExportButtons
                endpoint={config.exportEndpoint}
                params={{ q: query }}
                filename={config.exportFilename ?? 'records'}
                label={config.title}
              />
            )}
            {hideHeader && (
              <button type="button" className="btn btn-primary btn-sm push-right" onClick={() => setForm({})}>
                Add {config.noun.toLowerCase()}
              </button>
            )}
          </Toolbar>
          <DataTable columns={columns} rows={rows} rowKey={config.idKey} detail={config.rowDetail} emptyTitle={records.length ? 'No records match your search' : `No ${config.title.toLowerCase()} yet`} emptyHint={records.length ? undefined : `Use “Add ${config.noun.toLowerCase()}” to create the first one.`} />
        </section>
      )}
      {form && (
        <FormModal
          wide
          title={form.record ? `Edit ${config.noun.toLowerCase()}` : `Add ${config.noun.toLowerCase()}`}
          fields={editFields}
          initialValues={form.record ? Object.fromEntries(Object.entries(form.record).map(([k, v]) => [k, v ?? ''])) : {}}
          validate={config.validate}
          submitLabel={form.record ? 'Save changes' : 'Save'}
          onSubmit={save}
          onClose={() => setForm(null)}
        />
      )}
      {removing && <ConfirmDialog title={`Delete ${config.noun.toLowerCase()}`} message="Delete this record? This cannot be undone." confirmLabel="Delete" danger onConfirm={remove} onCancel={() => setRemoving(null)} />}
    </>
  )
}
