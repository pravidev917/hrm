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
