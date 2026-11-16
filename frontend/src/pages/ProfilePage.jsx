import { employeePortalApi } from '../api/employeeApi'
import { useFetch } from '../hooks/useFetch'
import PageHeader from '../components/common/PageHeader'
import StatusBadge from '../components/common/StatusBadge'
import Skeleton from '../components/common/Skeleton'
import { ErrorState } from '../components/common/Feedback'
import Icon from '../components/common/Icon'
import { formatDateTime } from '../utils/format'

export default function ProfilePage() {
  const { data, loading, error, reload } = useFetch(employeePortalApi.profile)

  return (
    <>
      <PageHeader title="My profile" subtitle="Your details are managed by your administrator." />
      {loading ? (
        <section className="card">
          <Skeleton rows={5} />
        </section>
      ) : error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : (
        <>
          <section className="card">
            <div style={{ display: 'flex', gap: 16, alignItems: 'center', flexWrap: 'wrap' }}>
              <span className="avatar" style={{ width: 56, height: 56, fontSize: '1.3rem' }} aria-hidden="true">
                {data.employeeName?.charAt(0)?.toUpperCase()}
              </span>
              <div style={{ minWidth: 0 }}>
                <h1 style={{ fontSize: 'var(--text-xl)' }}>{data.employeeName}</h1>
                <p className="muted" style={{ margin: 0 }}>
                  {data.role} · {data.email}
                </p>
              </div>
              <div className="spacer" />
              <StatusBadge value={data.accountStatus} />
            </div>
          </section>

          <section className="card">
            <h2>Account details</h2>
            <dl className="details">
              <div>
                <dt>Email</dt>
                <dd>{data.email}</dd>
              </div>
              <div>
                <dt>Designation</dt>
                <dd>{data.role}</dd>
              </div>
              <div>
                <dt>Phone</dt>
                <dd>{data.contact || '—'}</dd>
              </div>
              <div>
                <dt>Employee ID</dt>
                <dd className="mono">#{data.employeeId}</dd>
              </div>
              <div>
                <dt>Account status</dt>
                <dd>
                  <StatusBadge value={data.accountStatus} />
                </dd>
              </div>
              <div>
                <dt>Member since</dt>
                <dd>{formatDateTime(data.createdAt)}</dd>
              </div>
            </dl>
          </section>

          <div className="alert alert-info">
            <Icon name="info" size={16} />
            <span>
              Need a change to your name, designation, phone or email? Ask your administrator — profile
              details are read-only here by design.
            </span>
          </div>
        </>
      )}
    </>
  )
}
