import { useEffect, useMemo, useState } from 'react'
import Icon from './Icon'

/**
 * Month calendar of one employee's attendance.
 *
 * Day states, in priority order:
 *   approved-leave  (red)    — an APPROVED leave request covers the date
 *   present         (green)  — the employee marked PRESENT
 *   absent          (slate)  — the employee marked ABSENT
 *   approved-wfh    (blue)   — an APPROVED WFH request covers the date
 *   not-marked      (orange) — a working day in the past with no record
 *   sunday/future   (neutral) — nothing expected
 */

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

const pad = (n) => String(n).padStart(2, '0')
const toISO = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`

function startOfMonth(year, month) {
  return new Date(year, month, 1)
}

function buildWeeks(year, month) {
  const first = startOfMonth(year, month)
  // Monday-first grid: shift Sunday (0) to index 6.
  const offset = (first.getDay() + 6) % 7
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const cells = []

  for (let i = 0; i < offset; i += 1) cells.push(null)
  for (let day = 1; day <= daysInMonth; day += 1) cells.push(new Date(year, month, day))
  while (cells.length % 7 !== 0) cells.push(null)

  const weeks = []
  for (let i = 0; i < cells.length; i += 7) weeks.push(cells.slice(i, i + 7))
  return weeks
}

function expandLeaveDates(leaves) {
  const set = new Set()
  for (const leave of leaves) {
    if (!leave?.startDate || !leave?.endDate) continue
    const start = new Date(`${leave.startDate}T00:00:00`)
    const end = new Date(`${leave.endDate}T00:00:00`)
    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) continue
    for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
      set.add(toISO(d))
    }
  }
  return set
}

export default function AttendanceCalendar({
  records = [],
  leaveRecords = [],
  wfhRecords = [],
  holidayDateMap = new Map(),
  today,
  onSelectDate,
  onMonthChange,
}) {
  const now = new Date(`${today}T00:00:00`)
  const [cursor, setCursor] = useState({ year: now.getFullYear(), month: now.getMonth() })
  const [selected, setSelected] = useState(null)

  const byDate = useMemo(() => {
    const map = new Map()
    for (const r of records) map.set(r.attendanceDate, r)
    return map
  }, [records])

  const leaveDates = useMemo(() => expandLeaveDates(leaveRecords), [leaveRecords])
  const wfhDates = useMemo(() => expandLeaveDates(wfhRecords), [wfhRecords])

  const weeks = useMemo(() => buildWeeks(cursor.year, cursor.month), [cursor])

  const monthKey = `${cursor.year}-${pad(cursor.month + 1)}`

  const summary = useMemo(() => {
    let present = 0
    let absent = 0
    let leave = 0
    let wfh = 0
    let missing = 0
    for (const r of records) {
      if (!r.attendanceDate?.startsWith(monthKey)) continue
      if (r.status === 'PRESENT') present += 1
      else if (r.status === 'ABSENT') absent += 1
    }
    for (const iso of leaveDates) if (iso.startsWith(monthKey)) leave += 1
    for (const iso of wfhDates) if (iso.startsWith(monthKey)) wfh += 1
    const dim = new Date(cursor.year, cursor.month + 1, 0).getDate()
    for (let day = 1; day <= dim; day += 1) {
      const d = new Date(cursor.year, cursor.month, day)
      const iso = toISO(d)
      if (iso >= today) continue
      // Saturday is a working day here: only Sunday is a weekly holiday.
      if (d.getDay() === 0) continue
      if (!byDate.has(iso) && !leaveDates.has(iso)) missing += 1
    }
    return { present, absent, leave, wfh, missing }
  }, [records, leaveDates, wfhDates, byDate, monthKey, today, cursor])

  function stateOf(date) {
    const iso = toISO(date)
    if (leaveDates.has(iso)) return 'leave'
    const rec = byDate.get(iso)
    if (rec?.status === 'PRESENT') return 'present'
    if (rec?.status === 'ABSENT') return 'absent'
    if (wfhDates.has(iso)) return 'wfh'
      if (iso < today && date.getDay() !== 0) return 'missing'
    return 'none'
  }

  const label = startOfMonth(cursor.year, cursor.month).toLocaleDateString(undefined, {
    month: 'long',
    year: 'numeric',
  })

  // Keep the side panel in sync with the month the user is looking at.
  useEffect(() => {
    onMonthChange?.(`${cursor.year}-${pad(cursor.month + 1)}`)
  }, [cursor, onMonthChange])

  function shift(delta) {
    setCursor((c) => {
      const d = new Date(c.year, c.month + delta, 1)
      return { year: d.getFullYear(), month: d.getMonth() }
    })
    setSelected(null)
  }

  function goToday() {
    setCursor({ year: now.getFullYear(), month: now.getMonth() })
    setSelected(today)
  }

  function pick(date) {
    const iso = toISO(date)
    setSelected(iso)
    onSelectDate?.(iso, byDate.get(iso), leaveDates.has(iso))
  }

  const selectedRec = selected ? byDate.get(selected) : null
  const selectedIsLeave = selected ? leaveDates.has(selected) : false
  const selectedIsWfh = selected ? wfhDates.has(selected) : false
  const selectedHoliday = selected ? holidayDateMap.get(selected) : null

  return (
    <section className="card cal-card">
      <div className="cal-head">
        <div>
          <h2 className="cal-title">{label}</h2>
          <p className="muted small">
            {summary.present} present · {summary.leave} on leave · {summary.wfh} WFH · {summary.missing} unmarked
          </p>
        </div>
        <div className="cal-nav">
          <button type="button" className="icon-btn" onClick={() => shift(-1)} aria-label="Previous month">
            <Icon name="chevron-left" size={16} />
          </button>
          <button type="button" className="btn btn-sm" onClick={goToday}>
            Today
          </button>
          <button type="button" className="icon-btn" onClick={() => shift(1)} aria-label="Next month">
            <Icon name="chevron-right" size={16} />
          </button>
        </div>
      </div>

      <div className="cal-grid" role="grid" aria-label={`Attendance for ${label}`}>
        {WEEKDAYS.map((w) => (
          <div key={w} className="cal-dow" role="columnheader">
            {w}
          </div>
        ))}
        {weeks.flat().map((date, i) => {
          if (!date) return <div key={`e${i}`} className="cal-day cal-day--empty" aria-hidden="true" />
          const iso = toISO(date)
          const state = stateOf(date)
          const isToday = iso === today
          const isSelected = iso === selected
          return (
            <button
              key={iso}
              type="button"
              role="gridcell"
              className={`cal-day cal-day--${state}${isToday ? ' is-today' : ''}${isSelected ? ' is-selected' : ''}`}
              onClick={() => pick(date)}
              aria-label={`${date.toLocaleDateString()} — ${STATE_LABEL[state]}${
                holidayDateMap.has(iso) ? `, ${holidayDateMap.get(iso)}` : ''
              }`}
              aria-current={isToday ? 'date' : undefined}
            >
              <span className="cal-day-num">{date.getDate()}</span>
              {holidayDateMap.has(iso) && <span className="cal-holiday-dot" aria-hidden="true" />}
            </button>
          )
        })}
      </div>

      <ul className="cal-legend">
        <li>
          <span className="cal-swatch cal-swatch--present" /> Marked present
        </li>
        <li>
          <span className="cal-swatch cal-swatch--leave" /> Approved leave
        </li>
        <li>
          <span className="cal-swatch cal-swatch--absent" /> Marked absent
        </li>
        <li>
          <span className="cal-swatch cal-swatch--wfh" /> Approved WFH
        </li>
        <li>
          <span className="cal-swatch cal-swatch--missing" /> Not marked
        </li>
      </ul>

      {selected && (
        <div className="cal-detail">
          <strong>{new Date(`${selected}T00:00:00`).toLocaleDateString(undefined, { dateStyle: 'full' })}</strong>
          <span className="muted">
            {selectedHoliday
              ? `${selectedHoliday} — public holiday.`
              : selectedIsLeave
              ? 'Approved leave on this day.'
              : selectedRec
                ? selectedRec.status === 'PRESENT'
                  ? selectedRec.workMode === 'WFH'
                    ? 'Marked present — WFH.'
                    : selectedRec.workMode === 'OFFICE'
                      ? 'Marked present — office.'
                      : 'Marked present.'
                  : `Marked ${selectedRec.status.toLowerCase()}.`
                : selectedIsWfh
                  ? 'Approved work from home on this day — attendance still has to be marked.'
                  : selected < today
                    ? 'No attendance was recorded for this working day.'
                    : 'Nothing recorded — this day is not over yet.'}
          </span>
        </div>
      )}
    </section>
  )
}

const STATE_LABEL = {
  present: 'marked present',
  leave: 'approved leave',
  absent: 'marked absent',
  wfh: 'approved work from home',
  missing: 'not marked',
  none: 'no entry',
}
