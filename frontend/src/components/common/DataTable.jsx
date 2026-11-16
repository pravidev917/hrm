import { Fragment, useMemo, useState } from 'react'
import { EmptyState } from './Feedback'

/**
 * Client-side table: sorting and pagination operate on the rows already loaded from the API.
 * column: { key, label, render?(row), sortable?, sortValue?(row), align? }
 */
export default function DataTable({ columns, rows, rowKey, pageSize = 10, emptyTitle = 'Nothing here yet', emptyHint, emptyAction, detail }) {
  const [sort, setSort] = useState({ key: null, dir: 'asc' })
  const [page, setPage] = useState(0)
  const [openRow, setOpenRow] = useState(null)

  const sorted = useMemo(() => {
    if (!sort.key) return rows
    const col = columns.find((c) => c.key === sort.key)
    const get = col?.sortValue ?? ((r) => r[sort.key])
    const factor = sort.dir === 'asc' ? 1 : -1
    return [...rows].sort((a, b) => {
      const x = get(a)
      const y = get(b)
      if (x == null && y == null) return 0
      if (x == null) return 1
      if (y == null) return -1
      return (typeof x === 'number' && typeof y === 'number' ? x - y : String(x).localeCompare(String(y))) * factor
    })
  }, [rows, sort, columns])

  const pageCount = Math.max(1, Math.ceil(sorted.length / pageSize))
  const current = Math.min(page, pageCount - 1)
  const visible = sorted.slice(current * pageSize, current * pageSize + pageSize)

  if (rows.length === 0) return <EmptyState title={emptyTitle} hint={emptyHint} action={emptyAction} />

  const spanCols = columns.length + (detail ? 1 : 0)

  const toggleSort = (key) =>
    setSort((s) => (s.key === key ? { key, dir: s.dir === 'asc' ? 'desc' : 'asc' } : { key, dir: 'asc' }))

  return (
    <>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              {detail && <th scope="col" className="detail-toggle"><span className="sr-only">Details</span></th>}
              {columns.map((c) => {
                const active = sort.key === c.key
                return (
                  <th key={c.key} scope="col" className={c.align === 'right' ? 'right' : undefined} aria-sort={active ? (sort.dir === 'asc' ? 'ascending' : 'descending') : undefined}>
                    {c.sortable ? (
                      <button type="button" className="th-btn" onClick={() => toggleSort(c.key)}>
                        {c.label}
                        <span aria-hidden="true">{active ? (sort.dir === 'asc' ? ' ▲' : ' ▼') : ''}</span>
                      </button>
                    ) : (
                      c.label
                    )}
                  </th>
                )
              })}
            </tr>
          </thead>
          <tbody>
            {visible.map((row) => {
              const id = row[rowKey]
              const isOpen = openRow === id
              const pairs = detail ? detail(row).filter(([, v]) => v != null && v !== '') : []
              return (
                <Fragment key={id}>
                  <tr>
                    {detail && (
                      <td className="detail-toggle">
                        <button
                          type="button"
                          className="btn-ghost btn-icon"
                          aria-expanded={isOpen}
                          aria-label={isOpen ? 'Hide details' : 'Show details'}
                          onClick={() => setOpenRow(isOpen ? null : id)}
                        >
                          <span aria-hidden="true">{isOpen ? '▾' : '▸'}</span>
                        </button>
                      </td>
                    )}
                    {columns.map((c) => (
                      <td key={c.key} data-label={c.label} className={c.align === 'right' ? 'right' : undefined}>
                        {c.render ? c.render(row) : (row[c.key] ?? '—')}
                      </td>
                    ))}
                  </tr>
                  {isOpen && (
                    <tr className="detail-row">
                      <td colSpan={spanCols}>
                        {pairs.length === 0 ? (
                          <span className="muted">No additional details recorded.</span>
                        ) : (
                          <dl className="detail-grid">
                            {pairs.map(([label, value]) => (
                              <div key={label} className="detail-item">
                                <dt>{label}</dt>
                                <dd>{value}</dd>
                              </div>
                            ))}
                          </dl>
                        )}
                      </td>
                    </tr>
                  )}
                </Fragment>
              )
            })}
          </tbody>
        </table>
      </div>
      {pageCount > 1 && (
        <nav className="pager" aria-label="Pagination">
          <span className="muted small">
            Page {current + 1} of {pageCount} · {sorted.length} rows
          </span>
          <div>
            <button type="button" className="btn btn-sm" disabled={current === 0} onClick={() => setPage(current - 1)}>
              Previous
            </button>{' '}
            <button type="button" className="btn btn-sm" disabled={current >= pageCount - 1} onClick={() => setPage(current + 1)}>
              Next
            </button>
          </div>
        </nav>
      )}
    </>
  )
}
