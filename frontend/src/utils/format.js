const dateFormatter = new Intl.DateTimeFormat(undefined, { day: '2-digit', month: 'short', year: 'numeric' })
const dateTimeFormatter = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })

/** Today as YYYY-MM-DD in the user's local time zone (not UTC). */
export function todayISO() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function formatDate(value) {
  if (!value) return '—'
  // Plain dates (YYYY-MM-DD) are parsed as local dates to avoid off-by-one shifts.
  const d = /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(`${value}T00:00:00`) : new Date(value)
  return Number.isNaN(d.getTime()) ? '—' : dateFormatter.format(d)
}

export function formatDateTime(value) {
  if (!value) return '—'
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? '—' : dateTimeFormatter.format(d)
}

export function formatNumber(value) {
  return value == null ? '—' : new Intl.NumberFormat().format(value)
}

export function formatMoney(value) {
  return value == null
    ? '—'
    : new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(value)
}

export function titleCase(value) {
  if (!value) return '—'
  return String(value).toLowerCase().replace(/_/g, ' ').replace(/^\w|\s\w/g, (c) => c.toUpperCase())
}

/** Case-insensitive match of a query against the given keys of a record. */
export function matchesQuery(item, query, keys) {
  const q = query.trim().toLowerCase()
  if (!q) return true
  return keys.some((key) => String(item[key] ?? '').toLowerCase().includes(q))
}

/**
 * Attendance times are shown in the company's business timezone (IST), not in the
 * browser's timezone, because a report printed in Mumbai must read the same in London.
 */
const IST_ZONE = 'Asia/Kolkata'

const istTimeFormatter = new Intl.DateTimeFormat('en-IN', {
  timeZone: IST_ZONE,
  hour: '2-digit',
  minute: '2-digit',
  hour12: true,
})

const istDateTimeFormatter = new Intl.DateTimeFormat('en-IN', {
  timeZone: IST_ZONE,
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: true,
})

/** "09:12 am" in IST, or '—'. Instants only: a plain date has no time to show. */
export function formatTimeIST(value) {
  if (!value) return '—'
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? '—' : istTimeFormatter.format(d)
}

/** "18 Nov 2025, 09:12 am" in IST, or '—'. */
export function formatDateTimeIST(value) {
  if (!value) return '—'
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? '—' : istDateTimeFormatter.format(d)
}

/** Seconds as H:MM:SS (hours are not capped: a 26-hour total still reads correctly). */
export function formatDuration(seconds) {
  const total = Math.max(0, Math.floor(Number(seconds) || 0))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

/** Seconds as "2h 35m" for summary cards, where seconds are noise. */
export function formatHours(seconds) {
  const total = Math.max(0, Math.floor(Number(seconds) || 0))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  if (h === 0 && m === 0) return total > 0 ? '< 1m' : '0h 00m'
  return `${h}h ${String(m).padStart(2, '0')}m`
}

/** Seconds elapsed since an ISO instant, for the live counter on an open session. */
export function secondsSince(isoInstant, nowMs = Date.now()) {
  if (!isoInstant) return 0
  const started = new Date(isoInstant).getTime()
  if (Number.isNaN(started)) return 0
  return Math.max(0, Math.floor((nowMs - started) / 1000))
}
