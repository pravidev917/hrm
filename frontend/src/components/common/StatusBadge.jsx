import { titleCase } from '../../utils/format'

const TONES = {
  APPROVED: 'green',
  ACTIVE: 'green',
  PRESENT: 'green',
  JOINED: 'green',
  SELECTED: 'green',
  PENDING: 'amber',
  NOT_MARKED: 'gray',
  INACTIVE: 'gray',
  CANCELLED: 'gray',
  REJECTED: 'red',
  ABSENT: 'red',
  ON_LEAVE: 'blue',
  SOURCED: 'gray',
  INTERVIEW_SCHEDULED: 'blue',
  INTERVIEW_ATTENDED: 'amber',
  DROPPED: 'gray',
}

export default function StatusBadge({ value, label }) {
  if (value == null || value === '') return <span className="muted">—</span>
  const tone = TONES[String(value).toUpperCase()] ?? 'blue'
  return <span className={`badge badge-${tone}`}>{label ?? titleCase(value)}</span>
}
