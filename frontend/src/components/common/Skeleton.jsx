/**
 * Loading placeholders. Kept in their own file so it exports no non-component values
 * (Fast Refresh requires component-only modules).
 */
export default function Skeleton({ rows = 5, label = 'Loading…', height }) {
  return (
    <div role="status" aria-live="polite" aria-busy="true">
      <span className="sr-only">{label}</span>
      <div className="skeleton-stack" aria-hidden="true">
        {Array.from({ length: rows }).map((_, i) => (
          <div
            key={i}
            className="skeleton skeleton-row"
            style={{ width: `${100 - ((i * 7) % 25)}%`, height }}
          />
        ))}
      </div>
    </div>
  )
}
