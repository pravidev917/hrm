/**
 * Horizontal comparison bars (daily resumes per employee).
 *
 * rows: [{ id, label, value, isYou }]. The current employee's bar is highlighted
 * and every value is printed next to the bar, so the numbers stay readable
 * without relying on the visual length.
 */
export default function BarChart({ rows, unit = '', max, emptyLabel = 'No data for this date.' }) {
  if (!rows || rows.length === 0) return <p className="muted small">{emptyLabel}</p>

  const peak = max ?? Math.max(1, ...rows.map((r) => Number(r.value) || 0))

  return (
    <ul className="bars" aria-label="Comparison chart">
      {rows.map((row) => {
        const value = Number(row.value) || 0
        const width = peak === 0 ? 0 : Math.max(value > 0 ? 2 : 0, (value / peak) * 100)
        return (
          <li key={row.id} className={row.isYou ? 'bar-row is-you' : 'bar-row'}>
            <span className="bar-label" title={row.label}>
              {row.label}
              {row.isYou && <span className="lb-you">You</span>}
            </span>
            <span className="bar-track">
              <span className="bar-fill" style={{ width: `${width}%` }} aria-hidden="true" />
            </span>
            <span className="bar-value">
              {value}
              {unit && <span className="muted small"> {unit}</span>}
            </span>
          </li>
        )
      })}
    </ul>
  )
}
