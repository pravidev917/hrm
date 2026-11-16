import Icon from './Icon'

/**
 * Right-hand column of the attendance page.
 *
 * Shows what is happening in the month being viewed: holidays, birthdays and
 * work anniversaries (nearest first, past events dimmed) plus a resume-count
 * leaderboard built from real daily reports.
 */

const TYPE_META = {
  BIRTHDAY: { icon: 'sparkles', label: 'Birthday', tone: 'birthday' },
  ANNIVERSARY: { icon: 'bell', label: 'Work anniversary', tone: 'anniversary' },
  HOLIDAY: { icon: 'calendar', label: 'Holiday', tone: 'holiday' },
}

function relativeLabel(daysAway) {
  if (daysAway === 0) return 'Today'
  if (daysAway === 1) return 'Tomorrow'
  if (daysAway === -1) return 'Yesterday'
  if (daysAway > 1) return `In ${daysAway} days`
  return `${Math.abs(daysAway)} days ago`
}

function formatDay(iso) {
  return new Date(`${iso}T00:00:00`).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
}

export default function CalendarEventsPanel({ data, loading, error, onRetry }) {
  const events = data?.events ?? []
  const achievements = data?.achievements ?? []

  // Upcoming first (today, tomorrow, later), then what already happened.
  const upcoming = events.filter((e) => e.daysAway >= 0)
  const past = events.filter((e) => e.daysAway < 0)

  return (
    <aside className="events-panel" aria-label="This month's events">
      <section className="card events-card">
        <div className="events-head">
          <h2>This month</h2>
          {data?.month && (
            <span className="muted small">
              {new Date(`${data.month}T00:00:00`).toLocaleDateString(undefined, { month: 'long', year: 'numeric' })}
            </span>
          )}
        </div>

        {loading ? (
          <p className="muted small">Loading events…</p>
        ) : error ? (
          <div className="events-error">
            <p className="muted small">{error}</p>
            <button type="button" className="btn btn-sm" onClick={onRetry}>
              Retry
            </button>
          </div>
        ) : events.length === 0 ? (
          <p className="muted small">No birthdays, anniversaries or holidays this month.</p>
        ) : (
          <ul className="event-list">
            {[...upcoming, ...past].map((event) => {
              const meta = TYPE_META[event.type] ?? TYPE_META.HOLIDAY
              const isPast = event.daysAway < 0
              return (
                <li key={`${event.type}-${event.date}-${event.title}`} className={`event-item event-item--${meta.tone}${isPast ? ' is-past' : ''}`}>
                  <span className="event-icon" aria-hidden="true">
                    <Icon name={meta.icon} size={15} />
                  </span>
                  <div className="event-body">
                    <span className="event-title">{event.title}</span>
                    <span className="event-meta">
                      {formatDay(event.date)} · {relativeLabel(event.daysAway)}
                    </span>
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </section>

      <section className="card events-card">
        <div className="events-head">
          <h2>Top performers</h2>
          <span className="muted small">Resumes this month</span>
        </div>

        {loading ? (
          <p className="muted small">Loading…</p>
        ) : achievements.length === 0 ? (
          <p className="muted small">No daily reports filed this month yet.</p>
        ) : (
          <ol className="achievement-list">
            {achievements.map((a, i) => (
              <li key={a.employeeId} className="achievement-item">
                <span className={`achievement-rank${i === 0 ? ' is-first' : ''}`} aria-hidden="true">
                  {i + 1}
                </span>
                <div className="achievement-body">
                  <span className="achievement-name">{a.employeeName}</span>
                  <span className="event-meta">
                    {a.role?.toLowerCase() ?? '—'} · {a.reportsFiled} report{a.reportsFiled === 1 ? '' : 's'}
                  </span>
                </div>
                <span className="achievement-value">
                  {a.resumesSubmitted}
                  <span className="achievement-unit">resumes</span>
                </span>
              </li>
            ))}
          </ol>
        )}
      </section>
    </aside>
  )
}
