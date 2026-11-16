import { useCallback, useEffect, useMemo, useState } from 'react'
import { adminDailyReportApi, employeeDailyReportApi } from '../api/reportApi'
import { employeePortalApi } from '../api/employeeApi'
import { useAuth } from '../hooks/useAuth'
import { useFetch } from '../hooks/useFetch'
import { useToast } from '../hooks/useToast'
import PageHeader from '../components/common/PageHeader'
import DataTable from '../components/common/DataTable'
import FormModal from '../components/common/FormModal'
import ConfirmDialog from '../components/common/ConfirmDialog'
import { ErrorState, Spinner } from '../components/common/Feedback'
import { FilterSelect, SearchInput, Toolbar } from '../components/common/Toolbar'
import Icon from '../components/common/Icon'
import { formatDate, formatNumber, matchesQuery, todayISO } from '../utils/format'

const NUMBER = { type: 'number', min: 0, step: 1, half: true, required: true }

function validateReport(v) {
  const errors = {}
  if (!v.clientId) errors.clientId = 'Choose a client'
  if (Number(v.connectedCount) > Number(v.candidatesContacted))
    errors.connectedCount = 'Connected cannot exceed candidates contacted'
  if (Number(v.profilesSubmitted) > Number(v.candidatesSourced))
    errors.profilesSubmitted = 'Profiles submitted cannot exceed candidates sourced'
  return errors
}

export default function DailyReportsPage() {
  const { isAdmin } = useAuth()
  const toast = useToast()
  const fetcher = useCallback(() => (isAdmin ? adminDailyReportApi.list() : employeeDailyReportApi.list()), [isAdmin])
  const { data, loading, error, reload } = useFetch(fetcher)
  const [query, setQuery] = useState('')
  const [client, setClient] = useState('')
  const [date, setDate] = useState('')
  const [form, setForm] = useState(null)
  const [removing, setRemoving] = useState(null)

  // Employee client options come from the backend (scoped to the employee's admin).
  const [clients, setClients] = useState({ data: [], error: null, loading: !isAdmin })
  const loadClients = useCallback(() => {
    if (isAdmin) return
    employeePortalApi
      .clients()
      .then((rows) => setClients({ data: rows, error: null, loading: false }))
      .catch((e) => setClients({ data: [], error: e, loading: false }))
  }, [isAdmin])
  useEffect(loadClients, [loadClients])

  // Distinct clients already seen in reports (used as the admin filter).
  const clientsInReports = useMemo(() => {
    const map = new Map()
    for (const r of data ?? []) map.set(r.clientId, r.clientName)
    return [...map].map(([value, label]) => ({ value: String(value), label }))
  }, [data])

  const rows = useMemo(
    () =>
      (data ?? [])
        .filter(
          (r) =>
            (!client || String(r.clientId) === client) &&
            (!date || r.reportDate === date) &&
            matchesQuery(r, query, ['employeeName', 'clientName', 'remarks']),
        )
        .sort((a, b) => b.reportDate.localeCompare(a.reportDate)),
    [data, query, client, date],
  )

  const totals = useMemo(
    () =>
      rows.reduce(
        (acc, r) => ({
          profilesSubmitted: acc.profilesSubmitted + (r.profilesSubmitted ?? 0),
          candidatesSourced: acc.candidatesSourced + (r.candidatesSourced ?? 0),
          candidatesContacted: acc.candidatesContacted + (r.candidatesContacted ?? 0),
          connectedCount: acc.connectedCount + (r.connectedCount ?? 0),
        }),
        { profilesSubmitted: 0, candidatesSourced: 0, candidatesContacted: 0, connectedCount: 0 },
      ),
    [rows],
  )

  const fields = [
    { name: 'reportDate', label: 'Report date', type: 'date', max: todayISO(), required: true, half: true },
    {
      name: 'clientId',
      label: 'Client',
      type: 'select',
      options: clients.data.map((c) => ({ value: String(c.clientId), label: c.clientName })),
      required: true,
      half: true,
      placeholder: clients.data.length ? 'Select a client…' : 'No clients available',
      hint: clients.data.length ? undefined : 'Ask your administrator to add an active client first',
    },
    { name: 'profilesSubmitted', label: 'Profiles submitted', ...NUMBER },
    { name: 'candidatesSourced', label: 'Candidates sourced', ...NUMBER },
    { name: 'candidatesContacted', label: 'Candidates contacted', ...NUMBER },
    { name: 'connectedCount', label: 'Connected', ...NUMBER },
    { name: 'remarks', label: 'Remarks', type: 'textarea' },
  ]

  const columns = [
    { key: 'reportDate', label: 'Date', sortable: true, render: (r) => formatDate(r.reportDate) },
    ...(isAdmin ? [{ key: 'employeeName', label: 'Employee', sortable: true, render: (r) => <strong>{r.employeeName}</strong> }] : []),
    { key: 'clientName', label: 'Client', sortable: true },
    { key: 'candidatesSourced', label: 'Sourced', sortable: true, align: 'right' },
    { key: 'candidatesContacted', label: 'Contacted', sortable: true, align: 'right' },
    { key: 'connectedCount', label: 'Connected', sortable: true, align: 'right' },
    { key: 'profilesSubmitted', label: 'Submitted', sortable: true, align: 'right' },
    {
      key: 'remarks',
      label: 'Remarks',
      render: (r) => (r.remarks ? <span className="clamp" title={r.remarks}>{r.remarks}</span> : '—'),
    },
    ...(isAdmin
      ? []
      : [
          {
            key: 'actions',
            label: 'Actions',
            render: (r) => (
              <div className="row-actions">
                <button type="button" className="btn btn-sm" onClick={() => setForm({ report: r })}>
                  Edit
                </button>
                <button type="button" className="btn btn-sm btn-danger-ghost" onClick={() => setRemoving(r)}>
                  Delete
                </button>
              </div>
            ),
          },
        ]),
  ]

  async function save(v) {
    const body = {
      reportDate: v.reportDate,
      profilesSubmitted: Number(v.profilesSubmitted),
      candidatesSourced: Number(v.candidatesSourced),
      candidatesContacted: Number(v.candidatesContacted),
      connectedCount: Number(v.connectedCount),
      remarks: v.remarks.trim() || null,
    }
    if (form.report) {
      await employeeDailyReportApi.update(form.report.dailyReportId, v.clientId, body)
      toast.success('Report updated')
    } else {
      await employeeDailyReportApi.create(v.clientId, body)
      toast.success('Report submitted')
    }
    setForm(null)
    reload()
  }

  async function remove() {
    await employeeDailyReportApi.remove(removing.dailyReportId)
    toast.success('Report deleted')
    setRemoving(null)
    reload()
  }

  return (
    <>
      <PageHeader
        title="Daily reports"
        subtitle={
          isAdmin
            ? 'Daily recruitment activity across your team.'
            : 'Log your daily sourcing and submissions. One report per day.'
        }
        actions={
          !isAdmin && (
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => setForm({})}
              disabled={clients.loading || clients.data.length === 0}
              title={clients.data.length === 0 ? 'No active clients yet' : undefined}
            >
              <Icon name="plus" size={16} /> New report
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
          <Toolbar>
            <SearchInput
              value={query}
              onChange={setQuery}
              placeholder={isAdmin ? 'Search employee, client, remarks…' : 'Search client, remarks…'}
            />
            <FilterSelect
              label="Filter by client"
              value={client}
              onChange={setClient}
              options={[{ value: '', label: 'All clients' }, ...clientsInReports]}
            />
            <label className="filter">
              <span className="sr-only">Filter by date</span>
              <input type="date" value={date} onChange={(e) => setDate(e.target.value)} aria-label="Filter by date" />
            </label>
            {date && (
              <button type="button" className="btn btn-sm btn-ghost" onClick={() => setDate('')}>
                <Icon name="close" size={14} /> Clear date
              </button>
            )}
          </Toolbar>

          {rows.length > 0 && (
            <p className="muted small" style={{ marginTop: -8 }}>
              Showing {rows.length} report{rows.length === 1 ? '' : 's'}: {formatNumber(totals.candidatesSourced)} sourced ·{' '}
              {formatNumber(totals.candidatesContacted)} contacted · {formatNumber(totals.connectedCount)} connected ·{' '}
              {formatNumber(totals.profilesSubmitted)} profiles submitted
            </p>
          )}

          <DataTable
            columns={columns}
            rows={rows}
            rowKey="dailyReportId"
            emptyTitle={data.length ? 'No reports match your filters' : 'No daily reports yet'}
            emptyHint={
              !isAdmin && !data.length
                ? clients.data.length
                  ? 'Use “New report” to submit today’s numbers.'
                  : 'Your administrator has not added an active client yet.'
                : undefined
            }
          />
        </section>
      )}

      {clients.error && !isAdmin && (
        <div className="alert alert-error" role="alert">
          <Icon name="alert" size={16} />
          <span>
            We couldn’t load your client list, so reports can’t be filed right now.{' '}
            <button type="button" className="btn btn-sm btn-ghost" onClick={loadClients}>
              Retry
            </button>
          </span>
        </div>
      )}

      {form && (
        <FormModal
          wide
          title={form.report ? 'Edit daily report' : 'New daily report'}
          fields={fields}
          initialValues={
            form.report
              ? { ...form.report, clientId: String(form.report.clientId) }
              : {
                  reportDate: todayISO(),
                  clientId: '',
                  profilesSubmitted: 0,
                  candidatesSourced: 0,
                  candidatesContacted: 0,
                  connectedCount: 0,
                }
          }
          validate={validateReport}
          submitLabel={form.report ? 'Save changes' : 'Submit report'}
          onSubmit={save}
          onClose={() => setForm(null)}
        />
      )}

      {removing && (
        <ConfirmDialog
          title="Delete report"
          message={`Delete the report for ${formatDate(removing.reportDate)}? This cannot be undone.`}
          confirmLabel="Delete"
          danger
          onConfirm={remove}
          onCancel={() => setRemoving(null)}
        />
      )}
    </>
  )
}
