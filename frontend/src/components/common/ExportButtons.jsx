import { useState } from 'react'
import axios from 'axios'
import { apiClient, getErrorMessage } from '../../api/axiosClient'
import { useToast } from '../../hooks/useToast'
import Icon from './Icon'

/**
 * Two download buttons - CSV for any tool, Excel for a real workbook - for one
 * admin table. The backend renders the file, so the export always matches the
 * filters the browser is showing and never depends on what the page has loaded.
 *
 * The file name comes from the response headers; the fallback keeps the download
 * working if a proxy strips them.
 */
export default function ExportButtons({ endpoint, params, filename, label = 'Export' }) {
  const toast = useToast()
  const [busy, setBusy] = useState(null)

  async function download(format) {
    setBusy(format)
    try {
      const response = await apiClient.get(endpoint, {
        params: { ...clean(params), format },
        responseType: 'blob',
      })
      const header = response.headers?.['content-disposition'] ?? ''
      const match = /filename="?([^";]+)"?/.exec(header)
      const name = match?.[1] ?? `${filename}-${new Date().toISOString().slice(0, 10)}.${format}`

      const url = URL.createObjectURL(response.data)
      const link = document.createElement('a')
      link.href = url
      link.download = name
      link.rel = 'noopener'
      document.body.appendChild(link)
      link.click()
      // Chrome cancels a blob download if the anchor is detached or the URL
      // revoked in the same tick, so both happen a few seconds later.
      setTimeout(() => {
        link.remove()
        URL.revokeObjectURL(url)
      }, 4000)

      toast.success(`${label} ready — ${format === 'xlsx' ? 'Excel' : 'CSV'} file downloaded`)
    } catch (error) {
      // A failed download arrives as a blob, so unwrap the JSON message inside it.
      let message = getErrorMessage(error)
      const blob = error?.response?.data
      if (axios.isAxiosError(error) && blob instanceof Blob) {
        try {
          const text = await blob.text()
          const parsed = JSON.parse(text)
          if (typeof parsed?.message === 'string' && parsed.message.trim()) message = parsed.message
        } catch {
          // not JSON - keep the generic message
        }
      }
      toast.error(message)
    } finally {
      setBusy(null)
    }
  }

  return (
    <span className="export-buttons">
      <button
        type="button"
        className="btn btn-sm"
        disabled={busy !== null}
        onClick={() => download('csv')}
        title="Download this table as CSV"
      >
        <Icon name="download" size={14} /> {busy === 'csv' ? 'Preparing…' : 'CSV'}
      </button>
      <button
        type="button"
        className="btn btn-sm"
        disabled={busy !== null}
        onClick={() => download('xlsx')}
        title="Download this table as an Excel workbook"
      >
        <Icon name="download" size={14} /> {busy === 'xlsx' ? 'Preparing…' : 'Excel'}
      </button>
    </span>
  )
}

/** Drops empty filters so the URL stays clean (and the backend sees "no filter"). */
function clean(params = {}) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== '' && value !== null && value !== undefined),
  )
}
