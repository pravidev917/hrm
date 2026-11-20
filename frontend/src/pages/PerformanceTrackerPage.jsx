import { useCallback, useMemo, useState } from 'react'
import { trackerApi } from '../api/trackerApi'
import { useFetch } from '../hooks/useFetch'
import PageHeader from '../components/common/PageHeader'
import DataTable from '../components/common/DataTable'
import BarChart from '../components/common/BarChart'
import DonutChart from '../components/common/DonutChart'
import Skeleton from '../components/common/Skeleton'
import { EmptyState, ErrorState } from '../components/common/Feedback'
import Icon from '../components/common/Icon'
import { formatDate, formatDateTime, formatMoney, formatNumber } from '../utils/format'

const MEDAL = { 1: 'gold', 2: 'silver', 3: 'bronze' }

function thisMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

/** 'YYYY-MM' + delta months, without timezone surprises. */
function shiftMonth(month, delta) {
  const [year, index] = month.split('-').map(Number)
  const date = new Date(year, index - 1 + delta, 1)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

function monthLabel(month) {
  if (!month) return '—'
  const [year, index] = month.split('-').map(Number)
  return new Intl.DateTimeFormat(undefined, { month: 'long', year: 'numeric' }).format(new Date(year, index - 1, 1))
}

/**
 * Administrator performance tracker.
 *
 * Part 1: monthly daily-resume leaderboard (from daily reports).
 * Part 2: monthly revenue report per employee (from weekly reports) with the
 *         employee's own notes for that month.
 *
 * Months can be navigated freely and historical data is preserved, because every
 * figure is recomputed from the stored reports for the selected month.
 */
export default function PerformanceTrackerPage() {
  const [month, setMonth] = useState(thisMonth)
  const fetcher = useCallback(() => trackerApi.adminMonthly(month), [month])
  const { data, loading, error, reload } = useFetch(fetcher)

  const rows = useMemo(() => data?.leaderboard?.rows ?? [], [data])
  const totalResumes = data?.leaderboard?.totalResumes ?? 0
  const chartRows = useMemo(
    () => rows.slice(0, 10).map((r) => ({ id: r.employeeId, label: r.employeeName, value: r.resumesSubmitted })),
    [rows],
  )

  const revenueColumns = [
    {
      key: 'employeeName',
      label: 'Employee',
      sortable: true,
      render: (row) => (
        <div>
          <strong>{row.employeeName}</strong>
          <div className="cell-sub">{row.role || '—'}</div>
        </div>
      ),
    },
    {
      key: 'salary',
      label: 'Salary',
      sortable: true,
      align: 'right',
      render: (row) => (row.salary == null ? '—' : formatMoney(row.salary)),
    },
    {
      key: 'weeksCounted',
      label: 'Weeks',
      sortable: true,
      align: 'right',
      render: (row) => formatNumber(row.weeksCounted),
    },
    {
      key: 'monthlyRevenue',
      label: 'Monthly revenue',
      sortable: true,
      align: 'right',
      render: (row) => <strong>{formatMoney(row.monthlyRevenue)}</strong>,
    },
    {
      key: 'notes',
      label: 'Notes',
      align: 'right',
      render: (row) => (row.notes.length ? `${row.notes.length}` : '—'),
    },
  ]

  /** Row detail: the week-by-week revenue and the employee's own notes. */
  const revenueDetail = (row) => [
    ...row.weeks.map((week) => [
      `Week ${week.weekNumber ?? ''} · ${formatDate(week.weekStartDate)} – ${formatDate(week.weekEndDate)}`,
      `${formatMoney(week.revenue)} · ${week.profilesSubmitted ?? 0} profiles · ${week.interviewsScheduled ?? 0} interviews${
        week.clientName ? ` · ${week.clientName}` : ''
      }`,
    ]),
    ...row.notes.map((note) => [
      `Note${note.title ? `: ${note.title}` : ''} · ${formatDateTime(note.createdAt)}`,
      note.content,
    ]),
  ]

  const revenue = data?.revenue
  const employees = revenue?.employees ?? []

  return (
    <>
      <PageHeader
        title="Performance tracker"
        subtitle="Daily resume leaderboard and monthly revenue, calculated from your team's own daily and weekly reports."
      />

      <div className="toolbar">
        <div className="month-nav">
          <button
            type="button"
            className="btn btn-sm btn-ghost"
            onClick={() => setMonth((m) => shiftMonth(m, -1))}
            aria-label="Previous month"
          >
            <Icon name="chevron-left" size={16} />
          </button>
          <label className="filter">
            <span className="sr-only">Month</span>
            <input
              type="month"
              value={month}
              max={thisMonth()}
              onChange={(e) => setMonth(e.target.value || thisMonth())}
              aria-label="Select month"
            />
          </label>
          <button
            type="button"
            className="btn btn-sm btn-ghost"
            onClick={() => setMonth((m) => shiftMonth(m, 1))}
            disabled={month >= thisMonth()}
            aria-label="Next month"
          >
            <Icon name="chevron-right" size={16} />
          </button>
          {month !== thisMonth() && (
            <button type="button" className="btn btn-sm" onClick={() => setMonth(thisMonth())}>
              This month
            </button>
          )}
        </div>
        <button type="button" className="btn btn-sm btn-ghost push-right" onClick={reload}>
          <Icon name="refresh" size={14} /> Refresh
        </button>
        <span className="muted small">
          {monthLabel(month)} · {data ? `${formatDate(data.monthStart)} – ${formatDate(data.monthEnd)}` : '—'}
        </span>
      </div>

      {loading ? (
        <section className="card">
          <Skeleton rows={8} />
        </section>
      ) : error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : (
        <>
          <div className="stats">
            <div className="card stat">
              <div className="stat-top">
                <span className="stat-label">Resumes this month</span>
                <span className="stat-icon tone-green">
                  <Icon name="file" size={16} />
                </span>
              </div>
              <span className="stat-value">{formatNumber(totalResumes)}</span>
              <span className="stat-hint">
                {formatNumber(data.leaderboard.totalReports)} daily reports ·{' '}
                {data.leaderboard.employeesWithReports} of {rows.length} employees reported
              </span>
            </div>
            <div className="card stat">
              <div className="stat-top">
                <span className="stat-label">Revenue this month</span>
                <span className="stat-icon tone-green">
                  <Icon name="chart" size={16} />
                </span>
              </div>
              <span className="stat-value">{formatMoney(revenue.totalRevenue)}</span>
              <span className="stat-hint">
                {formatNumber(revenue.weeksCounted)} weekly reports · {revenue.employeesWithRevenue} employees earning
              </span>
            </div>
            <div className="card stat stat-center">
              <DonutChart
                percent={rows[0] ? Math.min(100, Math.round((totalResumes / Math.max(rows.length * data.dailyTarget, 1)) * 100)) : 0}
                centerValue={`${data.dailyTarget}/day`}
                centerLabel="daily target"
                size={120}
                caption="Team average against the daily target"
              />
            </div>
          </div>

          <section className="card">
            <div className="card-header">
              <div>
                <h2>Part 1 · Monthly resume leaderboard</h2>
                <p className="muted small">
                  Resumes submitted per employee this month. Employees with no reports stay listed with zero.
                </p>
              </div>
              <span className="chip">{rows.length} employees</span>
            </div>

            {rows.length === 0 ? (
              <EmptyState icon="users" title="No employees yet" hint="Add employees and their daily reports will rank here." />
            ) : (
              <>
                <BarChart rows={chartRows} unit="resumes" emptyLabel="No resume counts for this month yet." />
                <div className="lb-table-wrap">
                  <table className="lb-table">
                    <thead>
                      <tr>
                        <th scope="col" className="lb-rank-col">
                          #
                        </th>
                        <th scope="col">Employee</th>
                        <th scope="col">Designation</th>
                        <th scope="col" className="lb-num">
                          Resumes
                        </th>
                        <th scope="col" className="lb-num">
                          Reports
                        </th>
                        <th scope="col" className="lb-num">
                          Days
                        </th>
                        <th scope="col" className="lb-num">
                          Avg / day
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {rows.map((row) => (
                        <tr key={row.employeeId}>
                          <td className="lb-rank-col">
                            <span className={`lb-rank${MEDAL[row.rank] ? ` lb-rank--${MEDAL[row.rank]}` : ''}`}>
                              {row.rank}
                            </span>
                          </td>
                          <td>{row.employeeName}</td>
                          <td className="muted">{row.role || '—'}</td>
                          <td className="lb-num lb-num--lead">{formatNumber(row.resumesSubmitted)}</td>
                          <td className="lb-num">{formatNumber(row.reportsFiled)}</td>
                          <td className="lb-num">{row.daysReported}</td>
                          <td className="lb-num">{row.averageResumesPerDay.toFixed(2)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </>
            )}
          </section>

          <section className="card">
            <div className="card-header">
              <div>
                <h2>Part 2 · Monthly revenue report</h2>
                <p className="muted small">
                  Sum of each employee&apos;s weekly revenue for {monthLabel(month)}. A week counts in the month that
                  contains its start date, so four- and five-week months both add up and no week is counted twice.
                  Expand a row for the weeks and the employee&apos;s notes.
                </p>
              </div>
              <span className="chip">{formatMoney(revenue.totalRevenue)}</span>
            </div>
            <DataTable
              columns={revenueColumns}
              rows={employees}
              rowKey="employeeId"
              detail={revenueDetail}
              pageSize={8}
              emptyTitle="No employees yet"
              emptyHint="Revenue appears here once employees file weekly reports."
            />
          </section>
        </>
      )}
    </>
  )
}
