import { useCallback, useMemo, useState } from 'react'
import { adminWeeklyReportApi, employeeWeeklyReportApi } from '../api/reportApi'
import { useAuth } from '../hooks/useAuth'
import { useFetch } from '../hooks/useFetch'
import { useToast } from '../hooks/useToast'
import PageHeader from '../components/common/PageHeader'
import DataTable from '../components/common/DataTable'
import FormModal from '../components/common/FormModal'
import ConfirmDialog from '../components/common/ConfirmDialog'
import { ErrorState, Spinner } from '../components/common/Feedback'
import Icon from '../components/common/Icon'
import { SearchInput, Toolbar } from '../components/common/Toolbar'
import { formatDate, formatMoney, matchesQuery, todayISO } from '../utils/format'

const COUNT = { type: 'number', min: 0, step: 1, half: true, required: true }

const FIELDS = [
  { name: 'reportDate', label: 'Any date in the week', type: 'date', max: todayISO(), required: true, hint: 'The week (Mon–Sun) is worked out by the server' },
  { name: 'profilesSubmitted', label: 'Profiles submitted', ...COUNT },
  { name: 'interviewsScheduled', label: 'Interviews scheduled', ...COUNT },
  { name: 'finalSelections', label: 'Final selections', ...COUNT },
  { name: 'joined', label: 'Joined', ...COUNT },
  { name: 'revenueGenerated', label: 'Revenue generated', type: 'number', min: 0, step: '0.01', required: true },
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

  const rows = useMemo(
    () => (data ?? []).filter((r) => matchesQuery(r, query, ['employeeName'])).sort((a, b) => b.weekStartDate.localeCompare(a.weekStartDate)),
    [data, query],
  )

  const columns = [
    { key: 'weekStartDate', label: 'Week', sortable: true, render: (r) => <>{formatDate(r.weekStartDate)} – {formatDate(r.weekEndDate)}</> },
    ...(isAdmin ? [{ key: 'employeeName', label: 'Employee', sortable: true, render: (r) => <strong>{r.employeeName}</strong> }] : []),
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
      profilesSubmitted: Number(v.profilesSubmitted),
      interviewsScheduled: Number(v.interviewsScheduled),
      finalSelections: Number(v.finalSelections),
      joined: Number(v.joined),
      revenueGenerated: Number(v.revenueGenerated),
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
          initialValues={form.report ? { ...form.report, reportDate: form.report.weekStartDate } : { reportDate: todayISO(), profilesSubmitted: 0, interviewsScheduled: 0, finalSelections: 0, joined: 0, revenueGenerated: 0 }}
          submitLabel={form.report ? 'Save changes' : 'Submit report'}
          onSubmit={save}
          onClose={() => setForm(null)}
        />
      )}
      {removing && <ConfirmDialog title="Delete weekly report" message={`Delete the report for the week of ${formatDate(removing.weekStartDate)}? This cannot be undone.`} confirmLabel="Delete" danger onConfirm={remove} onCancel={() => setRemoving(null)} />}
    </>
  )
}
