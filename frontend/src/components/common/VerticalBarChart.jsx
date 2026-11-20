/**
 * Vertical bars for day-by-day series.
 *
 * data: [{ label, values: { submitted: 2, missing: 1 } }]
 * series: [{ key, label, tone }]
 * The exact values are repeated in a collapsible table for accessibility.
 */
export default function VerticalBarChart({
  data = [],
  series = [],
  height = 190,
  ariaLabel = 'Vertical bar chart',
  emptyLabel = 'No data for this range.',
  valueFormat = (value) => value,
}) {
  if (!data.length) return <p className="muted small">{emptyLabel}</p>

  const peak = Math.max(
    1,
    ...data.flatMap((row) => series.map((s) => Number(row.values?.[s.key]) || 0)),
  )

  return (
    <div className="vchart-wrap">
      <div className="vchart" style={{ height }} role="img" aria-label={ariaLabel}>
        {data.map((row) => (
          <div className="vcol" key={row.label}>
            <div className="vbars">
              {series.map((s) => {
                const value = Number(row.values?.[s.key]) || 0
                const barHeight = value === 0 ? 0 : Math.max(2, (value / peak) * 100)
                return (
                  <span
                    key={s.key}
                    className={`vbar vbar--${s.tone ?? 'purple'}`}
                    style={{ height: `${barHeight}%` }}
                    title={`${s.label}: ${valueFormat(value)}`}
                  />
                )
              })}
            </div>
            <span className="vlabel">{row.label}</span>
          </div>
        ))}
      </div>

      <ul className="legend">
        {series.map((s) => (
          <li key={s.key}>
            <span className={`legend-dot legend-dot--${s.tone ?? 'purple'}`} aria-hidden="true" />
            {s.label}
          </li>
        ))}
      </ul>

      <details className="chart-data">
        <summary>View chart data</summary>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Label</th>
                {series.map((s) => (
                  <th key={s.key}>{s.label}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {data.map((row) => (
                <tr key={row.label}>
                  <td>{row.label}</td>
                  {series.map((s) => (
                    <td key={s.key}>{valueFormat(Number(row.values?.[s.key]) || 0)}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </div>
  )
}
