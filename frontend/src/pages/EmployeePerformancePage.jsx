import { useCallback, useMemo, useState } from 'react'
import { trackerApi } from '../api/trackerApi'
import { notesApi } from '../api/notesApi'
import { useFetch } from '../hooks/useFetch'
import { todayISO } from '../utils/format'
import PageHeader from '../components/common/PageHeader'
import DonutChart from '../components/common/DonutChart'
import BarChart from '../components/common/BarChart'
import NotesPanel from '../components/common/NotesPanel'
import Skeleton from '../components/common/Skeleton'
import { DateFilter } from '../components/common/Toolbar'
import { EmptyState, ErrorState } from '../components/common/Feedback'
import Icon from '../components/common/Icon'
import { formatDate, formatMoney, formatNumber } from '../utils/format'

function monthLabel(month) {
  if (!month) return '—'
  const [year, index] = month.split('-').map(Number)
  return new Intl.DateTimeFormat(undefined, { month: 'long', year: 'numeric' }).format(new Date(year, index - 1, 1))
}

/**
 * The employee's own performance tracker.
 *
 * Part 1 daily resumes against a target of 5.
 * Part 2 monthly revenue against a target of salary × 3.
 * Part 3 today's resume comparison with the rest of the team (same organisation).
 * Part 4 the employee's own notes.
 *
 * Every figure comes from the signed-in employee's stored reports. The green ring
 * stops at 100% while the numbers keep telling the truth, so over-achievement is
 * visible without breaking the chart.
 */
export default function EmployeePerformancePage() {
  const [date, setDate] = useState(todayISO)
  const fetcher = useCallback(() => trackerApi.employee(date), [date])
  const { data, loading, error, reload } = useFetch(fetcher)

  const daily = data?.daily
  const revenue = data?.revenue
  const comparison = useMemo(
    () =>
      (data?.comparison ?? []).map((row) => ({
        id: row.employeeId,
        label: row.employeeName,
        value: row.resumes,
        isYou: row.isYou,
      })),
    [data],
  )

  return (
    <>
      <PageHeader
        title="My performance"
        subtitle="Your daily resume target, this month's revenue target and how the team is doing today."
        actions={
          <div className="page-header-actions">
            <DateFilter label="Date" value={date} onChange={setDate} />
            <button type="button" className="btn btn-sm btn-ghost" onClick={() => setDate(todayISO())} disabled={date === todayISO()}>
              <Icon name="refresh" size={14} /> Today
            </button>
          </div>
        }
      />

      {loading ? (
        <section className="card">
          <Skeleton rows={8} />
        </section>
      ) : error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : (
        <>
          <div className="grid-2 tracker-goals">
            <section className="card goal-card">
              <div className="card-header">
                <div>
                  <h2>Daily resume target</h2>
                  <p className="muted small">{formatDate(data.date ?? date)}</p>
                </div>
                <span className={daily.met ? 'chip chip-success' : 'chip'}>{daily.met ? 'Target met' : 'In progress'}</span>
              </div>
              <div className="goal-body">
                <DonutChart
                  percent={daily.percent}
                  centerValue={`${daily.achieved}/${daily.target}`}
                  centerLabel="resumes"
                  ariaLabel={`${daily.achieved} of ${daily.target} resumes submitted today`}
                  caption={`${daily.percent}% of today's target`}
                />
                <dl className="goal-facts">
                  <div>
                    <dt>Submitted</dt>
                    <dd>{formatNumber(daily.achieved)}</dd>
                  </div>
                  <div>
                    <dt>Target</dt>
                    <dd>{formatNumber(daily.target)}</dd>
                  </div>
                  <div>
                    <dt>Remaining</dt>
                    <dd className={daily.remaining > 0 ? 'tone-warn' : 'tone-success'}>
                      {daily.remaining > 0 ? formatNumber(daily.remaining) : 'Target reached'}
                    </dd>
                  </div>
                  <div>
                    <dt>Achievement</dt>
                    <dd>{daily.percent}%</dd>
                  </div>
                </dl>
              </div>
            </section>

            <section className="card goal-card">
              <div className="card-header">
                <div>
                  <h2>Monthly revenue target</h2>
                  <p className="muted small">
                    {monthLabel(data.month)} · {formatDate(data.monthStart)} – {formatDate(data.monthEnd)}
                  </p>
                </div>
                <span className={revenue.met ? 'chip chip-success' : 'chip'}>{revenue.met ? 'Target met' : 'In progress'}</span>
              </div>
              {revenue.target == null ? (
                <EmptyState
                  icon="chart"
                  title="No salary on your record yet"
                  hint="Your revenue target is three times your monthly salary. Ask your administrator to set your salary on your employee record."
                />
              ) : (
                <div className="goal-body">
                  <DonutChart
                    percent={revenue.percent}
                    centerValue={formatMoney(revenue.achieved)}
                    centerLabel={`of ${formatMoney(revenue.target)}`}
                    ariaLabel={`${revenue.percent}% of the monthly revenue target of ${formatMoney(revenue.target)}`}
                    caption={`${revenue.percent}% of ${formatMoney(revenue.target)} (salary × 3)`}
                  />
                  <dl className="goal-facts">
                    <div>
                      <dt>Revenue</dt>
                      <dd>{formatMoney(revenue.achieved)}</dd>
                    </div>
                    <div>
                      <dt>Target</dt>
                      <dd>{formatMoney(revenue.target)}</dd>
                    </div>
                    <div>
                      <dt>Remaining</dt>
                      <dd className={Number(revenue.remaining) > 0 ? 'tone-warn' : 'tone-success'}>
                        {Number(revenue.remaining) > 0 ? formatMoney(revenue.remaining) : 'Target reached'}
                      </dd>
                    </div>
                    <div>
                      <dt>Salary</dt>
                      <dd>{formatMoney(revenue.salary)}</dd>
                    </div>
                    <div>
                      <dt>Weekly reports</dt>
                      <dd>{formatNumber(revenue.weeksCounted)}</dd>
                    </div>
                    <div>
                      <dt>Achievement</dt>
                      <dd>{revenue.percent}%</dd>
                    </div>
                  </dl>
                </div>
              )}
            </section>
          </div>

          <section className="card">
            <div className="card-header">
              <div>
                <h2>Resumes submitted on {formatDate(data.date)}</h2>
                <p className="muted small">
                  Your own count is highlighted. Only resume totals are shown — no other employee&apos;s data is exposed.
                </p>
              </div>
              <span className="chip">{comparison.length} employees</span>
            </div>
            {comparison.length === 0 ? (
              <EmptyState icon="users" title="Nothing to compare yet" hint="No employee in your organisation has filed a report for this date." />
            ) : (
              <>
                <BarChart rows={comparison} unit="resumes" />
                <p className="muted small">
                  You submitted {formatNumber(comparison.find((r) => r.isYou)?.value ?? 0)} resume
                  {(comparison.find((r) => r.isYou)?.value ?? 0) === 1 ? '' : 's'} on this date.
                </p>
              </>
            )}
          </section>

          <NotesPanel
            api={notesApi.employee}
            title="My notes"
            subtitle="Only you can see these notes. They are saved to your account."
            placeholder="What do you want to remember or follow up on?"
            id="employee-notes"
          />
        </>
      )}
    </>
  )
}
