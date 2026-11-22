import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { sourcingAnalyticsApi } from '../../api/dashboardApi'
import { useFetch } from '../../hooks/useFetch'
import BarChart from './BarChart'
import DataTable from './DataTable'
import VerticalBarChart from './VerticalBarChart'
import { ErrorState } from './Feedback'
import Skeleton from './Skeleton'
import { formatDate, formatNumber } from '../../utils/format'

/**
 * "Check profiles source" - the admin view of sourcing activity.
 *
 * Every number here is counted on the server from daily reports: the table columns
 * (one per platform), the totals row, the per-employee chart and the per-platform
 * chart all read the same sums, so they cannot disagree with each other or with the
 * reports the employees filed. The range runs from the first daily report the company
 * ever filed to today - nothing is hard-coded.
 */
export default function SourcingAnalytics({ onAddNew }) {
  const { data, loading, error, reload } = useFetch(sourcingAnalyticsApi.summary)

  const platforms = useMemo(() => data?.platforms ?? [], [data])

  const columns = useMemo(
    () => [
      {
        key: 'employeeName',
        label: 'Employee name',
        sortable: true,
        render: (row) => <strong>{row.employeeName}</strong>,
      },
      { key: 'employeeCode', label: 'Employee ID', sortable: true, render: (row) => row.employeeCode || '—' },
      { key: 'designation', label: 'Designation', sortable: true, render: (row) => row.designation || '—' },
      ...platforms.map((platform) => ({
        key: `platform_${platform}`,
        label: platform,
        align: 'right',
        sortable: true,
        sortValue: (row) => row.platforms?.[platform] ?? 0,
        render: (row) => formatNumber(row.platforms?.[platform] ?? 0),
      })),
      {
        key: 'profilesSourced',
        label: 'Profiles sourced',
        align: 'right',
        sortable: true,
        render: (row) => <strong>{formatNumber(row.profilesSourced)}</strong>,
      },
      {
        key: 'profilesSubmitted',
        label: 'Submitted profiles',
        align: 'right',
        sortable: true,
        render: (row) => formatNumber(row.profilesSubmitted),
      },
    ],
    [platforms],
  )

  if (loading) {
    return (
      <section className="card">
        <Skeleton rows={6} />
      </section>
    )
  }
  if (error) return <ErrorState message={error} onRetry={reload} />

  const rows = (data?.employees ?? []).map((row) => ({ ...row, id: row.employeeId }))

  return (
    <>
      <section className="card">
        <div className="card-header">
          <div>
            <h2>Check profiles source</h2>
            <p className="muted small">
              Counted from {formatNumber(data.reportCount)} daily report
              {data.reportCount === 1 ? '' : 's'}, {formatDate(data.fromDate)} → {formatDate(data.toDate)} ·
              every figure is profiles sourced on that platform.
            </p>
          </div>
          {onAddNew ? (
            <button type="button" className="btn btn-primary btn-sm" onClick={onAddNew}>
              Add new
            </button>
          ) : (
            <Link to="/reports" className="small">
              Open reports →
            </Link>
          )}
        </div>

        <DataTable
          columns={columns}
          rows={rows}
          rowKey="id"
          emptyTitle="No sourcing recorded yet"
          emptyHint="Profiles appear here as soon as daily reports are filed, counted per employee and platform."
          emptyAction={
            onAddNew ? (
              <button type="button" className="btn btn-primary btn-sm" onClick={onAddNew}>
                Add a daily report
              </button>
            ) : undefined
          }
        />

        {rows.length > 0 && (
          <p className="muted small" style={{ marginTop: 12 }}>
            Totals:{' '}
            {(data.platformTotals ?? [])
              .map((row) => `${row.platform} ${formatNumber(row.profiles)}`)
              .join(' · ')}
          </p>
        )}
      </section>

      <div className="grid-2">
        <section className="card">
          <h2>Sourced profiles by employee</h2>
          <p className="muted small">Y axis: profiles sourced · X axis: employee names</p>
          <BarChart
            rows={(rows.length ? rows : []).map((row) => ({
              id: row.employeeId,
              label: row.employeeName,
              value: row.profilesSourced,
            }))}
            unit="profiles"
            emptyLabel="No profiles sourced in this range yet."
          />
        </section>

        <section className="card">
          <h2>Daily sourcing trend</h2>
          <p className="muted small">Y axis: profiles sourced · X axis: platform</p>
          <VerticalBarChart
            data={(data.platformTotals ?? []).map((row) => ({
              label: row.platform,
              values: { profiles: row.profiles },
            }))}
            series={[{ key: 'profiles', label: 'Profiles sourced', tone: 'purple' }]}
            ariaLabel="Profiles sourced per platform"
            emptyLabel="No platforms recorded in this range yet."
          />
        </section>
      </div>
    </>
  )
}
