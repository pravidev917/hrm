import { useCallback, useEffect, useState } from 'react'
import { getErrorMessage } from '../api/axiosClient'

/**
 * Loads data with loading / error / success state.
 * `fetcher` must be referentially stable (module-level function or useCallback).
 * A failed request always surfaces an error: no cached or mock fallback is used.
 */
export function useFetch(fetcher) {
  const [state, setState] = useState({ data: null, error: null, loading: true })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    fetcher()
      .then((data) => active && setState({ data, error: null, loading: false }))
      .catch(
        (error) =>
          active && setState({ data: null, error: getErrorMessage(error, 'Could not load data.'), loading: false }),
      )
    return () => {
      active = false
    }
  }, [fetcher, attempt])

  // Loading is switched on by the caller so the effect body stays side-effect free.
  const reload = useCallback(() => {
    setState((s) => ({ ...s, loading: true, error: null }))
    setAttempt((n) => n + 1)
  }, [])

  return { ...state, reload }
}

/** Converts a plain-array response into the paged shape the tables expect. */
function toPage(response, size) {
  if (Array.isArray(response)) {
    return { content: response, page: 0, size: response.length || size, totalElements: response.length, totalPages: 1 }
  }
  return response
}

/**
 * Paginated, server-filtered list loader.
 * `params` is any plain object of query parameters; changing it refetches from page 1.
 * Accepts either the paged envelope `{ content, page, size, totalElements, totalPages }`
 * or a plain array from endpoints that are not paginated yet.
 */
export function usePagedFetch(fetcher, params, { size = 10 } = {}) {
  const [query, setQuery] = useState(() => ({ page: 0, sort: { key: null, dir: 'asc' }, params }))
  const [state, setState] = useState({ data: null, error: null, loading: true })
  const [attempt, setAttempt] = useState(0)

  // Filters changed -> start again from the first page (derived state, no effect needed).
  const filterKey = JSON.stringify(params)
  const [seenFilterKey, setSeenFilterKey] = useState(filterKey)
  if (seenFilterKey !== filterKey) {
    setSeenFilterKey(filterKey)
    setQuery((q) => ({ ...q, page: 0, params }))
  } else if (query.params !== params) {
    // Keep the latest params object so the effect below sees a stable identity.
    setQuery((q) => ({ ...q, params }))
  }

  const { page, sort } = query

  useEffect(() => {
    let active = true
    const request = { ...params, page, size }
    if (sort.key) request.sort = `${sort.key},${sort.dir}`
    fetcher(request)
      .then((response) => active && setState({ data: toPage(response, size), error: null, loading: false }))
      .catch(
        (error) =>
          active &&
          setState({ data: null, error: getErrorMessage(error, 'Could not load this list.'), loading: false }),
      )
    return () => {
      active = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fetcher, filterKey, page, size, sort.key, sort.dir, attempt])

  const reload = useCallback(() => {
    setState((s) => ({ ...s, loading: true, error: null }))
    setAttempt((n) => n + 1)
  }, [])

  const setPage = useCallback((next) => {
    setState((s) => ({ ...s, loading: true, error: null }))
    setQuery((q) => ({ ...q, page: Math.max(0, next) }))
  }, [])

  const toggleSort = useCallback((key) => {
    setState((s) => ({ ...s, loading: true, error: null }))
    setQuery((q) => ({
      ...q,
      page: 0,
      sort: q.sort.key === key ? { key, dir: q.sort.dir === 'asc' ? 'desc' : 'asc' } : { key, dir: 'asc' },
    }))
  }, [])

  return {
    ...state,
    page,
    size,
    sort,
    setPage,
    toggleSort,
    reload,
    totalPages: state.data?.totalPages ?? 0,
    totalElements: state.data?.totalElements ?? 0,
  }
}
