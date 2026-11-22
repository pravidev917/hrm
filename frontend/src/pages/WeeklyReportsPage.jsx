import { useCallback, useEffect, useMemo, useState } from 'react'
import { adminWeeklyReportApi, employeeWeeklyReportApi } from '../api/reportApi'
import { clientApi } from '../api/adminApi'
import { employeePortalApi } from '../api/employeeApi'
import { useAuth } from '../hooks/useAuth'
import { useFetch } from '../hooks/useFetch'
import { useToast } from '../hooks/useToast'
import PageHeader from '../components/common/PageHeader'
import DataTable from '../components/common/DataTable'
import FormModal from '../components/common/FormModal'
import ConfirmDialog from '../components/common/ConfirmDialog'
import StatusBadge from '../components/common/StatusBadge'
import { ErrorState, Spinner } from '../components/common/Feedback'
import Icon from '../components/common/Icon'
import { SearchInput, Toolbar } from '../components/common/Toolbar'
import { formatDate, formatMoney, matchesQuery, todayISO } from '../utils/format'

const COUNT = { type: 'number', min: 0, step: 1, half: true, required: true }

const BASE_FIELDS = [
  { name: 'reportDate', label: 'Any date in the week', type: 'date', max: todayISO(), required: true, hint: 'The week (Mon–Sun) is worked out by the server' },
  { name: 'clientId', label: 'Client', type: 'select', options: [], placeholder: 'No client', hint: 'Optional · clients come from Client management' },
  { name: 'profilesSubmitted', label: 'Profiles submitted', ...COUNT },
  { name: 'interviewsScheduled', label: 'Interviews scheduled', ...COUNT },
  { name: 'finalSelections', label: 'Final selections', ...COUNT },
  { name: 'joined', label: 'Joined', ...COUNT },
  { name: 'revenueGenerated', label: 'Revenue generated', type: 'number', min: 0, step: '0.01', required: true },
  { name: 'candidateName', label: 'Candidate name', type: 'text', half: true, hint: 'The candidate this week’s numbers are about' },
  { name: 'position', label: 'Position', type: 'text', half: true, hint: 'Leave empty to use the client’s position' },
  {
    name: 'interviewStatus',
    label: 'Interview status',
    type: 'select',
    half: true,
    options: [
      { value: '', label: 'Not set' },
      { value: 'INTERVIEW_SCHEDULED', label: 'Interview scheduled' },
      { value: 'SELECTED', label: 'Selected' },
      { value: 'JOINED', label: 'Joined' },
      { value: 'REJECTED', label: 'Rejected' },
      { value: 'ON_HOLD', label: 'On hold' },
    ],
  },
]

export default function WeeklyReportsPage() {
  const { isAdmin } = useAuth()
  const api = isAdmin ? adminWeeklyReportApi : employeeWeeklyReportApi
  const toast = useToast()
  const fetcher = useCallback(() => api.list(), [api])
  const { data, loading, error, reload } = useFetch(fetcher)
  const [query, setQuery] = useState('')
  const [form, setForm] = useState(null)
  const [removing, setRemoving] = useState(null)

  // Client options: the employee's own organisation (or every client for an admin).
  const [clientOptions, setClientOptions] = useState([])
  useEffect(() => {
    const load = isAdmin
      ? clientApi.list().then((rows) =>
          rows.filter((c) => c.status === 'ACTIVE').map((c) => ({ value: String(c.clientId), label: c.clientCode ? `${c.clientCode} · ${c.clientName}` : c.clientName })),
        )
      : employeePortalApi.clients().then((rows) =>
          rows.map((c) => ({ value: String(c.clientId), label: c.clientCode ? `${c.clientCode} · ${c.clientName}` : c.clientName })),
        )
    load.then(setClientOptions).catch(() => setClientOptions([]))
  }, [isAdmin])

  const FIELDS = useMemo(
    () => BASE_FIELDS.map((f) => (f.name === 'clientId' ? { ...f, options: clientOptions } : f)),
    [clientOptions],
  )

  const rows = useMemo(
    () => (data ?? []).filter((r) => matchesQuery(r, query, ['employeeName'])).sort((a, b) => b.weekStartDate.localeCompare(a.weekStartDate)),
    [data, query],
  )

  const columns = [
    { key: 'weekStartDate', label: 'Week', sortable: true, render: (r) => <>{formatDate(r.weekStartDate)} – {formatDate(r.weekEndDate)}</> },
    { key: 'clientName', label: 'Client', sortable: true, render: (r) => r.clientName || '—' },
    { key: 'employeeName', label: 'Employee name', sortable: true, render: (r) => <strong>{r.employeeName}</strong> },
    { key: 'candidateName', label: 'Candidate name', sortable: true, render: (r) => r.candidateName || '—' },
    { key: 'position', label: 'Position', sortable: true, render: (r) => r.position || '—' },
    {
      key: 'interviewStatus',
      label: 'Interview status',
      sortable: true,
      render: (r) => (r.interviewStatus ? <StatusBadge value={r.interviewStatus} /> : '—'),
    },
    { key: 'profilesSubmitted', label: 'Profiles', sortable: true, align: 'right' },
    { key: 'interviewsScheduled', label: 'Interviews', sortable: true, align: 'right' },
    { key: 'finalSelections', label: 'Selections', sortable: true, align: 'right' },
    { key: 'joined', label: 'Joined', sortable: true, align: 'right' },
    { key: 'revenueGenerated', label: 'Revenue', sortable: true, align: 'right', render: (r) => formatMoney(r.revenueGenerated) },
    {
      key: 'actions',
      label: 'Actions',
      render: (r) => (
        <div className="row-actions">
          <button type="button" className="btn btn-sm" onClick={() => setForm({ report: r })}>Edit</button>
          <button type="button" className="btn btn-sm btn-danger-ghost" onClick={() => setRemoving(r)}>Delete</button>
        </div>
      ),
    },
  ]

  async function save(v) {
    const body = {
      reportDate: v.reportDate,
      clientId: v.clientId ? Number(v.clientId) : null,
      profilesSubmitted: Number(v.profilesSubmitted),
      interviewsScheduled: Number(v.interviewsScheduled),
      finalSelections: Number(v.finalSelections),
      joined: Number(v.joined),
      revenueGenerated: Number(v.revenueGenerated),
      candidateName: v.candidateName?.trim() || null,
      position: v.position?.trim() || null,
      interviewStatus: v.interviewStatus || null,
    }
    if (form.report) {
      await api.update(form.report.weeklyReportId, body)
      toast.success('Weekly report updated')
    } else {
      await api.create(body)
      toast.success('Weekly report submitted')
    }
    setForm(null)
    reload()
  }

  async function remove() {
    await api.remove(removing.weeklyReportId)
    toast.success('Weekly report deleted')
    setRemoving(null)
    reload()
  }

  return (
    <>
      <PageHeader
        title="Weekly reports"
        subtitle={isAdmin ? 'Weekly results across your team.' : 'Summarise your week: interviews, selections and joinings.'}
        actions={
          !isAdmin && (
            <button type="button" className="btn btn-primary" onClick={() => setForm({})}>
              <Icon name="plus" size={16} /> New weekly report
            </button>
          )
        }
      />
      {loading ? (
        <Spinner />
      ) : error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : (
        <section className="card">
          {isAdmin && (
            <Toolbar>
              <SearchInput value={query} onChange={setQuery} placeholder="Search employee…" />
            </Toolbar>
          )}
          <DataTable columns={columns} rows={rows} rowKey="weeklyReportId" emptyTitle={data.length ? 'No reports match your search' : 'No weekly reports yet'} emptyHint={!isAdmin && !data.length ? 'Use “New weekly report” to submit this week’s results.' : undefined} />
        </section>
      )}
      {form && (
        <FormModal
          wide
          title={form.report ? 'Edit weekly report' : 'New weekly report'}
          fields={FIELDS}
          initialValues={
            form.report
              ? {
                  ...form.report,
                  reportDate: form.report.weekStartDate,
                  clientId: form.report.clientId ? String(form.report.clientId) : '',
                }
              : {
                  reportDate: todayISO(),
                  clientId: '',
                  profilesSubmitted: 0,
                  interviewsScheduled: 0,
                  finalSelections: 0,
                  joined: 0,
                  revenueGenerated: 0,
                  candidateName: '',
                  position: '',
                  interviewStatus: '',
                }
          }
          submitLabel={form.report ? 'Save changes' : 'Submit report'}
          onSubmit={save}
          onClose={() => setForm(null)}
        />
      )}
      {removing && <ConfirmDialog title="Delete weekly report" message={`Delete the report for the week of ${formatDate(removing.weekStartDate)}? This cannot be undone.`} confirmLabel="Delete" danger onConfirm={remove} onCancel={() => setRemoving(null)} />}
    </>
  )
}
