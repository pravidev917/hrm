import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { adminDailyReportApi, employeeDailyReportApi } from '../api/reportApi'
import { sourcingPlatformApi } from '../api/reportingApi'
import { employeePortalApi } from '../api/employeeApi'
import { adminEmployeeApi, clientApi } from '../api/adminApi'
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

/**
 * The pipeline rule: every later stage comes out of the profiles that were sourced,
 * so no single stage may be larger than "candidates sourced".
 * 20 sourced / 15 submitted / 8 interviewed / 4 selected / 2 joined is valid;
 * 10 sourced / 15 submitted is not.
 */
function validateReport(v, platforms = []) {
  const errors = {}
  if (!v.clientId) errors.clientId = 'Choose a client'
  if (!v.employeeId && 'employeeId' in v) errors.employeeId = 'Choose an employee'

  const sourced = Number(v.candidatesSourced) || 0
  const submitted = Number(v.profilesSubmitted) || 0
  const contacted = Number(v.candidatesContacted) || 0
  const connected = Number(v.connectedCount) || 0

  if (submitted > sourced)
    errors.profilesSubmitted = `Profiles submitted (${submitted}) cannot be more than candidates sourced (${sourced})`
  if (contacted > sourced)
    errors.candidatesContacted = `Candidates contacted (${contacted}) cannot be more than candidates sourced (${sourced})`
  if (connected > sourced)
    errors.connectedCount = `Connected (${connected}) cannot be more than candidates sourced (${sourced})`
  if (connected > contacted)
    errors.connectedCount = 'Connected cannot exceed candidates contacted'

  const platformTotal = platforms.reduce((sum, name) => sum + (Number(v[`platform:${name}`]) || 0), 0)
  if (platformTotal !== Number(v.candidatesSourced)) {
    const key = `platform:${platforms[0] ?? ''}`
    errors[key] = `Platform counts add up to ${platformTotal}, but candidates sourced is ${Number(
      v.candidatesSourced,
    )}. They must match so the same profile is not counted twice.`
  }
  return errors
}

export default function DailyReportsPage() {
  const { isAdmin } = useAuth()
  const toast = useToast()
  const [searchParams, setSearchParams] = useSearchParams()
  const fetcher = useCallback(() => (isAdmin ? adminDailyReportApi.list() : employeeDailyReportApi.list()), [isAdmin])
  const { data, loading, error, reload } = useFetch(fetcher)
  const [query, setQuery] = useState('')
  const [client, setClient] = useState('')
  const [date, setDate] = useState('')
  const [form, setForm] = useState(searchParams.get('new') === '1' ? {} : null)
  const [removing, setRemoving] = useState(null)

  // Employee client options come from the backend (scoped to the employee's admin).
  const [platforms, setPlatforms] = useState([])
  useEffect(() => {
    const request = isAdmin ? sourcingPlatformApi.admin() : sourcingPlatformApi.employee()
    request
      .then((rows) => setPlatforms(rows.filter((p) => p.active).map((p) => p.name)))
      .catch(() => setPlatforms(['Naukri', 'Apna', 'LinkedIn', 'Careers', 'Others']))
  }, [isAdmin])

  const [clients, setClients] = useState({ data: [], error: null, loading: true })
  const loadClients = useCallback(() => {
    const request = isAdmin ? clientApi.list() : employeePortalApi.clients()
    request
      .then((rows) =>
        setClients({
          data: rows.filter((row) => (row.status ?? 'ACTIVE') === 'ACTIVE'),
          error: null,
          loading: false,
        }),
      )
      .catch((e) => setClients({ data: [], error: e, loading: false }))
  }, [isAdmin])

  // Administrators can file a report for any active employee ("Add New").
  const [employees, setEmployees] = useState([])
  useEffect(() => {
    if (!isAdmin) return
    adminEmployeeApi
      .list()
      .then((rows) => setEmployees(rows.filter((row) => row.accountStatus === 'APPROVED')))
      .catch(() => setEmployees([]))
  }, [isAdmin])
  useEffect(loadClients, [loadClients])

  // Arriving from the dashboard's "Add new" button opens the form once.
  useEffect(() => {
    if (searchParams.get('new') !== '1') return
    const next = new URLSearchParams(searchParams)
    next.delete('new')
    setSearchParams(next, { replace: true })
  }, [searchParams, setSearchParams])

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

  const platformFields = platforms.map((name) => ({
    name: `platform:${name}`,
    label: `${name} profiles`,
    type: 'number',
    min: 0,
    step: 1,
    half: true,
  }))

  const fields = [
    ...(isAdmin
      ? [
          {
            name: 'employeeId',
            label: 'Employee',
            type: 'select',
            options: employees.map((e) => ({
              value: String(e.employeeId),
              label: `${e.employeeName} · ${e.employeeCode || e.employeeId}`,
            })),
            required: true,
            half: true,
            placeholder: employees.length ? 'Select an employee…' : 'No active employees',
            hint: employees.length ? undefined : 'Add an employee first — every report belongs to an employee.',
          },
        ]
      : []),
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
    ...platformFields,
    { name: 'remarks', label: 'Remarks', type: 'textarea' },
  ]

  const columns = [
    { key: 'reportDate', label: 'Date', sortable: true, render: (r) => formatDate(r.reportDate) },
    ...(isAdmin
      ? [
          { key: 'employeeName', label: 'Employee name', sortable: true, render: (r) => <strong>{r.employeeName}</strong> },
          { key: 'employeeCode', label: 'Employee ID', sortable: true, render: (r) => r.employeeCode || '—' },
          { key: 'employeeDesignation', label: 'Designation', sortable: true, render: (r) => r.employeeDesignation || '—' },
        ]
      : []),
    { key: 'clientName', label: 'Client', sortable: true },
    {
      key: 'sourcingPlatform',
      label: 'Platform',
      render: (r) =>
        (r.platformCounts ?? []).length
          ? r.platformCounts.map((row) => `${row.platform} ${row.profileCount}`).join(', ')
          : r.sourcingPlatform || 'Unknown',
    },
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
    const platformCounts = platforms
      .map((name) => ({ platform: name, profileCount: Number(v[`platform:${name}`]) || 0 }))
      .filter((row) => row.profileCount > 0)
    const dominant = [...platformCounts].sort((a, b) => b.profileCount - a.profileCount)[0]

    const body = {
      reportDate: v.reportDate,
      profilesSubmitted: Number(v.profilesSubmitted),
      candidatesSourced: Number(v.candidatesSourced),
      candidatesContacted: Number(v.candidatesContacted),
      connectedCount: Number(v.connectedCount),
      sourcingPlatform: dominant?.platform ?? null,
      platformCounts,
      remarks: v.remarks.trim() || null,
    }
    if (form.report) {
      await employeeDailyReportApi.update(form.report.dailyReportId, v.clientId, body)
      toast.success('Report updated')
      setForm(null)
    } else if (isAdmin) {
      await adminDailyReportApi.create(v.employeeId, v.clientId, body)
      toast.success('Report added — add another or close')
    } else {
      await employeeDailyReportApi.create(v.clientId, body)
      toast.success('Report submitted — add another or close')
    }
    // A brand new report keeps the dialog open so the next one for the same day is one
    // click away; the employee and date are kept, the counts and the client reset.
    if (!form.report) {
      setForm({
        again: Date.now(),
        keep: {
          ...(isAdmin ? { employeeId: String(v.employeeId) } : {}),
          reportDate: v.reportDate,
        },
      })
    }
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
            : 'Log your daily sourcing and submissions. Add as many reports as you need — per client, several times a day.'
        }
        actions={
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => setForm({})}
            disabled={
              clients.loading ||
              clients.data.length === 0 ||
              (isAdmin && employees.length === 0)
            }
            title={
              clients.data.length === 0
                ? 'No active clients yet'
                : isAdmin && employees.length === 0
                  ? 'No active employees yet'
                  : undefined
            }
          >
            <Icon name="plus" size={16} /> {isAdmin ? 'Add new' : 'New report'}
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
                  ? 'Use “New report” to submit today’s numbers. You can add more than one report per day — one per client, or several for the same client.'
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
          key={form.again ?? form.report?.dailyReportId ?? 'new-report'}
          wide
          title={form.report ? 'Edit daily report' : 'New daily report'}
          fields={fields}
          initialValues={{
            ...(form.report
              ? {
                  ...form.report,
                  clientId: String(form.report.clientId),
                  employeeId: String(form.report.employeeId ?? ''),
                }
              : {
                  ...(isAdmin ? { employeeId: '' } : {}),
                  reportDate: todayISO(),
                  ...(form.keep ?? {}),
                  clientId: '',
                  profilesSubmitted: 0,
                  candidatesSourced: 0,
                  candidatesContacted: 0,
                  connectedCount: 0,
                }),
            ...Object.fromEntries(
              platforms.map((name) => [
                `platform:${name}`,
                form.report?.platformCounts?.find((row) => row.platform === name)?.profileCount ?? 0,
              ]),
            ),
          }}
          validate={(values) => validateReport(values, platforms)}
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
