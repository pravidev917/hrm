import Icon from './Icon'

export function SearchInput({ value, onChange, placeholder = 'Search…' }) {
  return (
    <label className="search">
      <span className="sr-only">Search</span>
      <Icon name="search" size={16} />
      <input
        type="search"
        value={value}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
        autoComplete="off"
      />
    </label>
  )
}

export function FilterSelect({ label, value, onChange, options }) {
  return (
    <label className="filter">
      <span className="sr-only">{label}</span>
      <select value={value} onChange={(e) => onChange(e.target.value)} aria-label={label}>
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </label>
  )
}

/** Date filter with an explicit clear action (native date inputs have no empty state on all platforms). */
export function DateFilter({ label, value, onChange }) {
  return (
    <div className="filter" style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
      <input type="date" value={value} onChange={(e) => onChange(e.target.value)} aria-label={label} />
      {value && (
        <button type="button" className="btn btn-sm btn-ghost" onClick={() => onChange('')} aria-label={`Clear ${label}`}>
          <Icon name="close" size={14} />
        </button>
      )}
    </div>
  )
}

export function Toolbar({ children, right }) {
  return (
    <div className="toolbar">
      {children}
      {right && <div className="toolbar-right">{right}</div>}
    </div>
  )
}
