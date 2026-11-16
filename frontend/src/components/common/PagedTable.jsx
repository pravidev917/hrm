import { EmptyState } from './Feedback'
import Skeleton from './Skeleton'

/**
 * Server-driven table used with usePagedFetch.
 * Sorting and paging are requested from the API (not applied to a partial page in memory),
 * which is what keeps totals and page counts honest on large lists.
 *
 * columns: { key, label, sortKey?, render?(row), align? }
 */
export default function PagedTable({
  columns,
  rows,
  rowKey,
  loading,
  sort,
  onSort,
  page,
  totalPages,
  totalElements,
  onPageChange,
  emptyTitle = 'Nothing here yet',
  emptyHint,
  emptyAction,
}) {
  if (loading) return <Skeleton rows={6} label="Loading rows…" />
  if (rows.length === 0) return <EmptyState title={emptyTitle} hint={emptyHint} action={emptyAction} />

  const from = totalElements === 0 ? 0 : page * rows.length + 1

  return (
    <>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              {columns.map((c) => {
                const key = c.sortKey ?? c.key
                const active = c.sortKey !== null && sort?.key === key
                return (
                  <th
                    key={c.key}
                    scope="col"
                    className={c.align === 'right' ? 'right' : undefined}
                    aria-sort={active ? (sort.dir === 'asc' ? 'ascending' : 'descending') : undefined}
                  >
                    {c.sortKey === null || !onSort ? (
                      c.label
                    ) : (
                      <button type="button" className="th-btn" onClick={() => onSort(key)}>
                        {c.label}
                        <span className={`th-sort${active ? ' active' : ''}`} aria-hidden="true">
                          {active ? (sort.dir === 'asc' ? '▲' : '▼') : '↕'}
                        </span>
                      </button>
                    )}
                  </th>
                )
              })}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row[rowKey]}>
                {columns.map((c) => (
                  <td key={c.key} data-label={c.label} className={c.align === 'right' ? 'right' : undefined}>
                    {c.render ? c.render(row) : (row[c.key] ?? '—')}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <nav className="pager" aria-label="Pagination">
          <span className="muted small">
            Page {page + 1} of {totalPages} · {totalElements} {totalElements === 1 ? 'row' : 'rows'}
            {from > 0 ? ` (showing from ${from})` : ''}
          </span>
          <div className="pager-buttons">
            <button type="button" className="btn btn-sm" disabled={page <= 0} onClick={() => onPageChange(page - 1)}>
              Previous
            </button>
            <button
              type="button"
              className="btn btn-sm"
              disabled={page >= totalPages - 1}
              onClick={() => onPageChange(page + 1)}
            >
              Next
            </button>
          </div>
        </nav>
      )}
    </>
  )
}
