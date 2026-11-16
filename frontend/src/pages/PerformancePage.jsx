import { useCallback, useState } from 'react'
import { performanceApi } from '../api/recordsApi'
import { getErrorMessage } from '../api/axiosClient'
import { useAuth } from '../hooks/useAuth'
import { useFetch } from '../hooks/useFetch'
import PageHeader from '../components/common/PageHeader'
import { ErrorState, Spinner } from '../components/common/Feedback'
import Icon from '../components/common/Icon'
import { FilterSelect, Toolbar } from '../components/common/Toolbar'
import RecordsPage from '../components/common/RecordsPage'
import { performanceConfig } from './performanceConfig'

/** Only the period changes; 'all' means no date filter. */
const PERIOD_OPTIONS = [
  { value: 'all', label: 'All time' },
  { value: 'thisMonth', label: 'This month' },
  { value: 'lastMonth', label: 'Last month' },
]

function currentMonth(offset = 0) {
  const d = new Date()
  const m = new Date(d.getFullYear(), d.getMonth() + offset, 1)
  return `${m.getFullYear()}-${String(m.getMonth() + 1).padStart(2, '0')}`
}

function toApiPeriod(period) {
  if (period === 'all') return 'all'
  if (period === 'lastMonth') return currentMonth(-1)
  return currentMonth(0)
}

const MEDAL = { 1: 'gold', 2: 'silver', 3: 'bronze' }

export default function PerformancePage() {
  const { isAdmin } = useAuth()
  const [tab, setTab] = useState('leaderboard')
  const [period, setPeriod] = useState('thisMonth')

  const apiPeriod = toApiPeriod(period)
  const fetcher = useCallback(() => performanceApi.leaderboard(apiPeriod), [apiPeriod])
  const { data, loading, error, reload } = useFetch(fetcher)

  const roles = data?.roles ?? []
  const totalPeople = roles.reduce((sum, r) => sum + r.employeeCount, 0)

  return (
    <>
      <PageHeader
        title="Performance"
        subtitle={
          isAdmin
            ? 'Your whole team, ranked inside each job designation.'
            : 'How you compare with colleagues who share your designation.'
        }
      />

      <div className="segmented tab-bar" role="tablist" aria-label="Performance views">
        {[
          ['leaderboard', 'Leaderboard'],
          ['records', 'Notes & ratings'],
        ].map(([value, label]) => (
          <button
            key={value}
            type="button"
            role="tab"
            aria-selected={tab === value}
            className={tab === value ? 'seg active' : 'seg'}
            onClick={() => setTab(value)}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === 'leaderboard' && (
        <>
          <Toolbar>
            <FilterSelect
              label="Period"
              value={period}
              onChange={setPeriod}
              options={PERIOD_OPTIONS.map((o) => ({ value: o.value, label: o.label }))}
            />
            <button type="button" className="btn btn-sm btn-ghost" onClick={reload}>
              <Icon name="refresh" size={14} /> Refresh
            </button>
            <span className="muted small push-right">
              Ranked by resumes submitted · {totalPeople} {totalPeople === 1 ? 'person' : 'people'}
            </span>
          </Toolbar>

          {loading ? (
            <Spinner />
          ) : error ? (
            <ErrorState message={getErrorMessage(error)} onRetry={reload} />
          ) : roles.length === 0 ? (
            <section className="card">
              <p className="muted">No employees yet, so there is nothing to rank.</p>
            </section>
          ) : (
            roles.map((group) => (
              <section className="card lb-card" key={group.role}>
                <div className="lb-head">
                  <h2>{group.role}</h2>
                  <span className="muted small">
                    {group.employeeCount} {group.employeeCount === 1 ? 'employee' : 'employees'}
                  </span>
                </div>

                <div className="lb-table-wrap">
                  <table className="lb-table">
                    <thead>
                      <tr>
                        <th scope="col" className="lb-rank-col">#</th>
                        <th scope="col">Employee</th>
                        <th scope="col" className="lb-num">Resumes</th>
                        <th scope="col" className="lb-num">Sourced</th>
                        <th scope="col" className="lb-num">Contacted</th>
                        <th scope="col" className="lb-num">Connected</th>
                        <th scope="col" className="lb-num">Reports</th>
                      </tr>
                    </thead>
                    <tbody>
                      {group.rows.map((row) => (
                        <tr key={row.employeeId} className={row.isYou ? 'is-you' : undefined}>
                          <td className="lb-rank-col">
                            <span className={`lb-rank${MEDAL[row.rank] ? ` lb-rank--${MEDAL[row.rank]}` : ''}`}>
                              {row.rank}
                            </span>
                          </td>
                          <td>
                            <span className="lb-name">{row.employeeName}</span>
                            {row.isYou && <span className="lb-you">You</span>}
                          </td>
                          <td className="lb-num lb-num--lead">{row.resumesSubmitted}</td>
                          <td className="lb-num">{row.candidatesSourced}</td>
                          <td className="lb-num">{row.candidatesContacted}</td>
                          <td className="lb-num">{row.connectedCount}</td>
                          <td className="lb-num">{row.reportsFiled}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
            ))
          )}

          <p className="muted small">
            Employees are compared only with colleagues in the same designation, because targets differ by role.
            Ties are broken by how many reports were filed, then by name.
          </p>
        </>
      )}

      {tab === 'records' && <RecordsPage config={performanceConfig} hideHeader />}
    </>
  )
}
