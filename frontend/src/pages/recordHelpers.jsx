/**
 * Shared helpers for the config-driven record pages.
 *
 * These live outside `recordPages.jsx` so that file only exports React
 * components, which is what the Fast Refresh lint rule requires.
 */

export const clean = (value) => {
  const v = typeof value === 'string' ? value.trim() : value
  return v === '' || v == null ? null : v
}

export const clamp = (text) => (text ? <span className="clamp" title={text}>{text}</span> : '—')
