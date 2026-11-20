import { useMemo, useState } from 'react'
import BarChart from './BarChart'
import DonutChart from './DonutChart'
import VerticalBarChart from './VerticalBarChart'
import Skeleton from './Skeleton'
import { EmptyState, ErrorState } from './Feedback'
import { useAuth } from '../../hooks/useAuth'
import { useFetch } from '../../hooks/useFetch'
import { reportingApi, sourcingPlatformApi } from '../../api/reportingApi'
import { adminEmployeeApi, clientApi } from '../../api/adminApi'
import { employeePortalApi } from '../../api/employeeApi'
import { formatDate, formatMoney, formatNumber, todayISO } from '../../utils/format'

const monthStart = () => `${todayISO().slice(0, 8)}01`

const EMPTY_FILTERS = {
  from: monthStart(),
  to: todayISO(),
  employeeId: '',
  clientId: '',
  position: '',
  platform: '',
}

const moneyOrMissing = (value) => (value == null ? 'Target not configured' : formatMoney(value))

/**
 * Server-computed reporting charts.
 *
 * The dashboard only renders what reportingApi returns; it never sums records
 * in the browser, so filtered totals, targets and client budgets stay
 * consistent between the administrator and employee views.
 */
export default function ReportingPanel() {
  const { isAdmin } = useAuth()
  const [draft, setDraft] = useState(EMPTY_FILTERS)
  const [applied, setApplied] = useState(EMPTY_FILTERS)

  const optionsFetcher = useMemo(
    () => async () => {
      if (isAdmin) {
        const [employees, clients, platforms] = await Promise.all([
          adminEmployeeApi.list(),
          clientApi.list(),
          sourcingPlatformApi.admin(),
        ])
        return { employees, clients, platforms }
      }
      const [clients, platforms] = await Promise.all([
        employeePortalApi.clients(),
        sourcingPlatformApi.employee(),
      ])
      return { employees: [], clients, platforms }
    },
    [isAdmin],
  )
  const { data: options } = useFetch(optionsFetcher)

  const fetcher = useMemo(() => {
    const params = {}
    Object.entries(applied).forEach(([key, value]) => {
      if (value !== '' && value != null) params[key] = value
    })
    return () => (isAdmin ? reportingApi.admin(params) : reportingApi.employee(params))
  }, [applied, isAdmin])

  const { data, loading, error, reload } = useFetch(fetcher)

  const set = (key) => (event) => setDraft((d) => ({ ...d, [key]: event.target.value }))
  const apply = (event) => {
    event.preventDefault()
    setApplied(draft)
  }
  const reset = () => {
    setDraft(EMPTY_FILTERS)
    setApplied(EMPTY_FILTERS)
  }

  return (
    <section className="reporting" aria-labelledby="reporting-heading">
      <div className="card">
        <div className="card-header">
          <div>
            <h2 id="reporting-heading">Reporting &amp; revenue</h2>
            <p className="muted small">
              Reports, sourced profiles, salary targets, client budgets and platform effectiveness.
            </p>
          </div>
        </div>

        <form className="filters reporting-filters" onSubmit={apply}>
          <label className="filter">
            <span className="small muted">From</span>
            <input type="date" value={draft.from} max={draft.to} onChange={set('from')} />
          </label>
          <label className="filter">
            <span className="small muted">To</span>
            <input type="date" value={draft.to} min={draft.from} onChange={set('to')} />
          </label>

          {isAdmin && (
            <label className="filter">
              <span className="small muted">Employee</span>
              <select value={draft.employeeId} onChange={set('employeeId')}>
                <option value="">All employees</option>
                {(options?.employees ?? []).map((employee) => (
                  <option key={employee.employeeId} value={employee.employeeId}>
                    {employee.employeeName}
                  </option>
                ))}
              </select>
            </label>
          )}

          <label className="filter">
            <span className="small muted">Client</span>
            <select value={draft.clientId} onChange={set('clientId')}>
              <option value="">All clients</option>
              {(options?.clients ?? []).map((client) => (
                <option key={client.clientId} value={client.clientId}>
                  {client.clientName}
                  {client.position ? ` — ${client.position}` : ''}
                </option>
              ))}
            </select>
          </label>

          <label className="filter">
            <span className="small muted">Position</span>
            <input
              type="search"
              value={draft.position}
              placeholder="Any position"
              maxLength={100}
              onChange={set('position')}
            />
          </label>

          <label className="filter">
            <span className="small muted">Platform</span>
            <select value={draft.platform} onChange={set('platform')}>
              <option value="">All platforms</option>
              {(options?.platforms ?? []).map((platform) => (
                <option key={platform.sourcingPlatformId} value={platform.name}>
                  {platform.name}
                </option>
              ))}
              <option value="Unknown">Unknown</option>
            </select>
          </label>

          <div className="filter-actions">
            <button type="submit" className="btn btn-primary">
              Apply filters
            </button>
            <button type="button" className="btn btn-ghost" onClick={reset}>
              Reset
            </button>
          </div>
        </form>

        <p className="muted small">
          {applied.from} → {applied.to}
          {data?.timezone ? ` · company timezone ${data.timezone}` : ''}
          {data?.partialMonths ? ' · partial months are labelled, not annualised' : ''}
        </p>
      </div>

      {loading ? (
        <div className="card">
          <Skeleton rows={8} />
        </div>
      ) : error ? (
        <div className="card">
          <ErrorState message={error} onRetry={reload} />
        </div>
      ) : !data ? (
        <div className="card">
          <EmptyState title="No reporting data" hint="Adjust the filters and try again." />
        </div>
      ) : (
        <ReportingBody data={data} isAdmin={isAdmin} />
      )}
    </section>
  )
}

function ReportingBody({ data, isAdmin }) {
  const {
    dailyReports,
    sourcedProfiles,
    revenueTargets,
    clientBudgets,
    platformEffectiveness,
    pipeline,
    recentCandidates,
  } = data

  return (
    <>
      <div className="stats reporting-kpis">
        <div className="card mini-stat">
          <span className="mini-label">Daily reports submitted</span>
          <span className="mini-value">{formatNumber(dailyReports.submittedReports)}</span>
          <span className="muted small">
            {formatNumber(dailyReports.missingReports)} missing across{' '}
            {formatNumber(dailyReports.eligibleEmployees)} reporting employees
          </span>
        </div>
        <div className="card mini-stat">
          <span className="mini-label">Profiles sourced</span>
          <span className="mini-value">{formatNumber(sourcedProfiles.totalSourced)}</span>
          <span className="muted small">
            {formatNumber(sourcedProfiles.unknownPlatformProfiles)} with platform Unknown
          </span>
        </div>
        <div className="card mini-stat">
          <span className="mini-label">Revenue in range</span>
          <span className="mini-value">{formatMoney(revenueTargets.totalRevenue)}</span>
          <span className="muted small">Recognised from weekly reports</span>
        </div>
        <div className="card mini-stat">
          <span className="mini-label">Monthly targets</span>
          <span className="mini-value">
            {revenueTargets.employeesWithoutTarget > 0
              ? `${formatNumber(revenueTargets.employeesWithoutTarget)} not configured`
              : formatMoney(revenueTargets.totalTarget)}
          </span>
          <span className="muted small">Salary × 3 per employee</span>
        </div>
      </div>

      <section className="card">
        <div className="card-header">
          <div>
            <h2>Daily reports</h2>
            <p className="muted small">Submitted versus missing, with sourced profiles.</p>
          </div>
          <span className="badge">{formatNumber(dailyReports.sourcedProfiles)} profiles</span>
        </div>
        <VerticalBarChart
          ariaLabel="Daily reports submitted and missing"
          data={dailyReports.days.map((day) => ({
            label: day.date.slice(8),
            values: {
              submitted: day.submittedReports,
              missing: day.missingReports,
              sourced: day.sourcedProfiles,
            },
          }))}
          series={[
            { key: 'submitted', label: 'Reports submitted', tone: 'purple' },
            { key: 'missing', label: 'Missing reports', tone: 'amber' },
            { key: 'sourced', label: 'Profiles sourced', tone: 'green' },
          ]}
          emptyLabel="No days in this range."
        />
        <p className="muted small">{dailyReports.missingBasis}</p>
      </section>

      <div className="grid-2">
        <section className="card">
          <div className="card-header">
            <div>
              <h2>Sourced profiles by employee</h2>
              <p className="muted small">One profile is counted once, from its authoritative record.</p>
            </div>
          </div>
          <VerticalBarChart
            ariaLabel="Sourced profiles by employee"
            data={sourcedProfiles.byEmployee.map((row) => ({
              label: row.employeeName,
              values: { sourced: row.sourcedProfiles },
            }))}
            series={[{ key: 'sourced', label: 'Profiles sourced', tone: 'purple' }]}
            emptyLabel="No profiles sourced in this range."
          />
          {sourcedProfiles.candidatesWithoutDate > 0 && (
            <p className="muted small">
              {formatNumber(sourcedProfiles.candidatesWithoutDate)} candidate record(s) have no sourced date
              and are excluded from the date-filtered totals.
            </p>
          )}
        </section>

        <section className="card">
          <div className="card-header">
            <div>
              <h2>Daily sourcing trend</h2>
              <p className="muted small">Profiles sourced per day.</p>
            </div>
          </div>
          <VerticalBarChart
            ariaLabel="Profiles sourced per day"
            data={sourcedProfiles.dailyTrend.map((point) => ({
              label: point.date.slice(8),
              values: { sourced: point.sourcedProfiles },
            }))}
            series={[{ key: 'sourced', label: 'Profiles sourced', tone: 'purple' }]}
            emptyLabel="No sourcing activity in this range."
          />
        </section>
      </div>

      <section className="card">
        <div className="card-header">
          <div>
            <h2>Revenue against target</h2>
            <p className="muted small">
              {revenueTargets.months?.join(', ')}
              {revenueTargets.partialMonths ? ' · partial month' : ''}
            </p>
          </div>
        </div>

        {revenueTargets.employees.length === 0 ? (
          <EmptyState title="No employees in scope" hint="There is nobody to show targets for." />
        ) : (
          <>
            <div className="target-grid">
              {revenueTargets.employees.map((employee) => (
                <article className="target-card" key={employee.employeeId}>
                  <h3>{employee.employeeName}</h3>
                  <DonutChart
                    percent={employee.targetConfigured ? employee.achievementPercent ?? 0 : 0}
                    centerValue={
                      employee.targetConfigured ? `${formatNumber(employee.rawAchievementPercent)}%` : '—'
                    }
                    centerLabel={employee.targetConfigured ? 'of target' : 'not configured'}
                    caption={
                      employee.targetConfigured
                        ? `${formatMoney(employee.revenue)} of ${formatMoney(employee.target)}`
                        : 'Salary is not configured for every month in the range.'
                    }
                    tone={employee.targetConfigured ? 'green' : 'amber'}
                    size={150}
                    ariaLabel={`${employee.employeeName} achieved ${formatNumber(
                      employee.rawAchievementPercent,
                    )} percent of target`}
                  />
                  {employee.targetConfigured && Number(employee.excess) > 0 && (
                    <p className="small muted">Excess {formatMoney(employee.excess)}</p>
                  )}
                  {employee.targetConfigured && (
                    <p className="small muted">Remaining {formatMoney(employee.remaining)}</p>
                  )}
                </article>
              ))}
            </div>

            <div className="table-wrap">
              <table className="table">
                <caption className="sr-only">Employee revenue and targets</caption>
                <thead>
                  <tr>
                    <th>Employee</th>
                    <th>Month-effective salary</th>
                    <th>Target</th>
                    <th>Revenue</th>
                    <th>Achieved</th>
                    <th>Remaining</th>
                    <th>Excess</th>
                  </tr>
                </thead>
                <tbody>
                  {revenueTargets.employees.map((employee) => (
                    <tr key={employee.employeeId}>
                      <td>{employee.employeeName}</td>
                      <td>{moneyOrMissing(employee.salary)}</td>
                      <td>{moneyOrMissing(employee.target)}</td>
                      <td>{formatMoney(employee.revenue)}</td>
                      <td>{employee.targetConfigured ? `${formatNumber(employee.rawAchievementPercent)}%` : '—'}</td>
                      <td>{employee.targetConfigured ? formatMoney(employee.remaining) : '—'}</td>
                      <td>{employee.targetConfigured ? formatMoney(employee.excess) : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="muted small">{revenueTargets.basis}</p>
          </>
        )}
      </section>

      <section className="card">
        <div className="card-header">
          <div>
            <h2>Client budget vs generated revenue</h2>
            <p className="muted small">
              Budgets are capacity, not revenue: openings × budget per candidate.
            </p>
          </div>
        </div>

        {clientBudgets.clients.length === 0 ? (
          <EmptyState title="No client activity" hint="No client has budget or revenue in this range." />
        ) : (
          <>
            <VerticalBarChart
              ariaLabel="Client budget versus actual revenue"
              data={clientBudgets.clients.map((client) => ({
                label: client.clientName,
                values: {
                  budget: client.totalBudget ?? 0,
                  revenue: client.actualRevenue ?? 0,
                },
              }))}
              series={[
                { key: 'budget', label: 'Client budget', tone: 'purple' },
                { key: 'revenue', label: 'Actual revenue', tone: 'green' },
              ]}
              valueFormat={formatMoney}
            />
            <div className="table-wrap">
            <table className="table">
              <caption className="sr-only">Client budgets and attributable revenue</caption>
              <thead>
                <tr>
                  <th>Client / position</th>
                  <th>Openings</th>
                  <th>Per candidate</th>
                  <th>Total budget</th>
                  <th>Actual revenue</th>
                  <th>Remaining</th>
                  <th>Employee breakdown</th>
                </tr>
              </thead>
              <tbody>
                {clientBudgets.clients.map((client) => (
                  <tr key={client.clientId}>
                    <td>
                      <strong>{client.clientName}</strong>
                      <span className="muted small"> {client.position || '—'}</span>
                    </td>
                    <td>{formatNumber(client.openings)}</td>
                    <td>{formatMoney(client.budgetPerCandidate)}</td>
                    <td>
                      {client.budgetConfigured ? formatMoney(client.totalBudget) : 'Budget not configured'}
                    </td>
                    <td>{formatMoney(client.actualRevenue)}</td>
                    <td>{client.budgetConfigured ? formatMoney(client.remainingBudget) : '—'}</td>
                    <td>
                      {client.employees.length === 0
                        ? '—'
                        : client.employees
                            .map((row) => `${row.employeeName}: ${formatMoney(row.revenue)}`)
                            .join(' · ')}
                    </td>
                  </tr>
                ))}
              </tbody>
              </table>
            </div>
          </>
        )}
        {Number(clientBudgets.unattributedRevenue) > 0 && (
          <p className="muted small">
            {formatMoney(clientBudgets.unattributedRevenue)} of revenue has no client attribution and is
            shown separately, not under a client.
          </p>
        )}
        <p className="muted small">{clientBudgets.basis}</p>
      </section>

      <section className="card">
        <div className="card-header">
          <div>
            <h2>Sourcing platform effectiveness</h2>
            <p className="muted small">Sourced, shortlisted, interviews, offers and joins.</p>
          </div>
        </div>
        {platformEffectiveness.length === 0 ? (
          <EmptyState title="No platform activity" hint="No candidates or reports match these filters." />
        ) : (
          <>
            <BarChart
              rows={platformEffectiveness.map((row) => ({
                id: row.platform,
                label: row.platform,
                value: row.sourcedProfiles,
              }))}
              emptyLabel="No platform activity."
            />
            <div className="table-wrap">
              <table className="table">
                <caption className="sr-only">Platform conversion detail</caption>
                <thead>
                  <tr>
                    <th>Platform</th>
                    <th>Sourced</th>
                    <th>Shortlisted</th>
                    <th>Interviews</th>
                    <th>Offers</th>
                    <th>Joined</th>
                    <th>Conversion</th>
                    <th>Attributable revenue</th>
                  </tr>
                </thead>
                <tbody>
                  {platformEffectiveness.map((row) => (
                    <tr key={row.platform}>
                      <td>
                        {row.platform}
                        <span className="muted small"> {row.outcomeSource}</span>
                      </td>
                      <td>{formatNumber(row.sourcedProfiles)}</td>
                      <td>{formatNumber(row.shortlisted)}</td>
                      <td>{formatNumber(row.interviews)}</td>
                      <td>{formatNumber(row.offers)}</td>
                      <td>{formatNumber(row.joined)}</td>
                      <td title={row.note}>
                        {row.conversionRate == null
                          ? 'Not tracked'
                          : `${formatNumber(row.conversionRate)}% (${formatNumber(row.joined)} / ${formatNumber(
                              row.conversionDenominator,
                            )} sourced)`}
                      </td>
                      <td>{row.attributableRevenue == null ? 'Not attributable' : formatMoney(row.attributableRevenue)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="muted small">
              Conversion uses candidate records only. Report-only counts have no candidate outcomes, so their
              conversion is shown as not tracked rather than guessed.
            </p>
          </>
        )}
      </section>

      <div className="grid-2">
        <section className="card">
          <div className="card-header">
            <div>
              <h2>Candidate pipeline</h2>
              <p className="muted small">Candidate records in this range.</p>
            </div>
          </div>
          <BarChart
            rows={pipeline
              .filter((stage) => stage.candidates > 0)
              .map((stage) => ({ id: stage.stage, label: stage.stage, value: stage.candidates }))}
            emptyLabel="No candidate records in this range."
          />
        </section>

        <section className="card">
          <div className="card-header">
            <div>
              <h2>Recent candidates</h2>
              <p className="muted small">Latest sourced records, newest first.</p>
            </div>
          </div>
          {recentCandidates.length === 0 ? (
            <p className="muted small">No candidate records in this range.</p>
          ) : (
            <ul className="list-plain">
              {recentCandidates.map((candidate) => (
                <li key={candidate.feedbackId}>
                  <strong>{candidate.candidateName}</strong>
                  <span className="muted small">
                    {' '}
                    · {candidate.status || 'Unknown'} · {candidate.sourcingPlatform || 'Unknown'} ·{' '}
                    {formatDate(candidate.sourcedDate)}
                    {isAdmin && candidate.employeeName ? ` · ${candidate.employeeName}` : ''}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </>
  )
}
