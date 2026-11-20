/**
 * Donut chart for "achieved vs remaining" (daily resumes, monthly revenue).
 *
 * Pure SVG, no chart dependency. The green arc is the achieved share, capped at
 * the target (so 7 of 5 resumes still draws a full ring while the centre keeps
 * showing the real number). A text version is included for screen readers.
 */
export default function DonutChart({
  percent = 0,
  centerValue,
  centerLabel,
  caption,
  tone = 'green',
  size = 190,
  ariaLabel,
}) {
  const clamped = Math.max(0, Math.min(100, Number.isFinite(percent) ? percent : 0))
  const radius = 48
  const circumference = 2 * Math.PI * radius
  const dash = (clamped / 100) * circumference

  return (
    <div className="donut" style={{ width: size }}>
      <svg viewBox="0 0 120 120" width={size} height={size} role="img" aria-label={ariaLabel ?? `${clamped}%`}>
        <circle className="donut-track" cx="60" cy="60" r={radius} fill="none" strokeWidth="14" />
        <circle
          className={`donut-fill donut-fill--${tone}`}
          cx="60"
          cy="60"
          r={radius}
          fill="none"
          strokeWidth="14"
          strokeLinecap="round"
          strokeDasharray={`${dash} ${circumference}`}
          transform="rotate(-90 60 60)"
        />
        <text className="donut-value" x="60" y="58" textAnchor="middle">
          {centerValue}
        </text>
        {centerLabel && (
          <text className="donut-label" x="60" y="74" textAnchor="middle">
            {centerLabel}
          </text>
        )}
      </svg>
      {caption && <p className="donut-caption small muted">{caption}</p>}
    </div>
  )
}
