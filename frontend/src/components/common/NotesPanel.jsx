import { useCallback, useMemo, useState } from 'react'
import { useFetch } from '../../hooks/useFetch'
import { useToast } from '../../hooks/useToast'
import { getErrorMessage } from '../../api/axiosClient'
import ConfirmDialog from './ConfirmDialog'
import FormModal from './FormModal'
import { Alert, EmptyState, ErrorState } from './Feedback'
import Skeleton from './Skeleton'
import Icon from './Icon'
import { formatDateTime } from '../../utils/format'

/**
 * Persistent personal notes (employee notes and the admin notepad).
 *
 * `api` is one of the note APIs (notesApi.employee / notesApi.admin); the server
 * decides whose notes those are, so nothing here can point at another account.
 *
 * Used in three places on purpose — the employee performance page, the admin
 * dashboard and the admin notepad — instead of a second note store.
 */
export default function NotesPanel({
  api,
  title = 'Notes',
  subtitle,
  placeholder = 'Write your thoughts…',
  limit,
  id = 'notes',
}) {
  const toast = useToast()
  const fetcher = useCallback(() => api.list(), [api])
  const { data, loading, error, reload } = useFetch(fetcher)

  const [draft, setDraft] = useState({ title: '', content: '' })
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState('')
  const [editing, setEditing] = useState(null)
  const [removing, setRemoving] = useState(null)

  const notes = useMemo(() => {
    const rows = data ?? []
    return limit ? rows.slice(0, limit) : rows
  }, [data, limit])

  async function create(e) {
    e.preventDefault()
    if (!draft.content.trim()) {
      setSaveError('Write something in the note first')
      return
    }
    setSaving(true)
    setSaveError('')
    try {
      await api.create({ title: draft.title.trim() || null, content: draft.content.trim() })
      setDraft({ title: '', content: '' })
      toast.success('Note saved')
      reload()
    } catch (err) {
      setSaveError(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  async function saveEdit(values) {
    await api.update(editing.noteId, {
      title: values.title.trim() || null,
      content: values.content.trim(),
    })
    toast.success('Note updated')
    setEditing(null)
    reload()
  }

  async function remove() {
    await api.remove(removing.noteId)
    toast.success('Note deleted')
    setRemoving(null)
    reload()
  }

  return (
    <section className="card notes-card" id={id}>
      <div className="card-header">
        <div>
          <h2>{title}</h2>
          {subtitle && <p className="muted small">{subtitle}</p>}
        </div>
        {data && <span className="chip">{data.length}</span>}
      </div>

      <form className="notes-form" onSubmit={create}>
        <label className="sr-only" htmlFor={`${id}-title`}>
          Note title
        </label>
        <input
          id={`${id}-title`}
          type="text"
          maxLength={150}
          placeholder="Title (optional)"
          value={draft.title}
          onChange={(e) => setDraft((d) => ({ ...d, title: e.target.value }))}
        />
        <label className="sr-only" htmlFor={`${id}-content`}>
          Note
        </label>
        <textarea
          id={`${id}-content`}
          rows={3}
          maxLength={5000}
          placeholder={placeholder}
          value={draft.content}
          onChange={(e) => setDraft((d) => ({ ...d, content: e.target.value }))}
        />
        {saveError && <Alert>{saveError}</Alert>}
        <div className="notes-form-actions">
          <span className="muted small">{draft.content.length}/5000</span>
          <button type="submit" className="btn btn-primary btn-sm" disabled={saving || !draft.content.trim()}>
            <Icon name="plus" size={14} /> {saving ? 'Saving…' : 'Save note'}
          </button>
        </div>
      </form>

      {loading ? (
        <Skeleton rows={3} />
      ) : error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : notes.length === 0 ? (
        <EmptyState icon="edit" title="No notes yet" hint="Anything you write here is saved and stays available to you." />
      ) : (
        <ul className="notes-list">
          {notes.map((note) => (
            <li key={note.noteId} className="note-item">
              <div className="note-head">
                <strong>{note.title || 'Untitled note'}</strong>
                <div className="row-actions">
                  <button type="button" className="btn btn-sm btn-ghost" onClick={() => setEditing(note)}>
                    <Icon name="edit" size={14} /> Edit
                  </button>
                  <button type="button" className="btn btn-sm btn-danger-ghost" onClick={() => setRemoving(note)}>
                    <Icon name="trash" size={14} /> Delete
                  </button>
                </div>
              </div>
              <p className="note-body">{note.content}</p>
              <p className="muted small note-meta">
                Created {formatDateTime(note.createdAt)}
                {note.updatedAt && note.updatedAt !== note.createdAt && ` · Updated ${formatDateTime(note.updatedAt)}`}
              </p>
            </li>
          ))}
        </ul>
      )}

      {editing && (
        <FormModal
          title="Edit note"
          fields={[
            { name: 'title', label: 'Title (optional)', maxLength: 150 },
            { name: 'content', label: 'Note', type: 'textarea', required: true, maxLength: 5000 },
          ]}
          initialValues={{ title: editing.title ?? '', content: editing.content ?? '' }}
          submitLabel="Save changes"
          onSubmit={saveEdit}
          onClose={() => setEditing(null)}
        />
      )}

      {removing && (
        <ConfirmDialog
          title="Delete note"
          message="Delete this note? This cannot be undone."
          confirmLabel="Delete"
          danger
          onConfirm={remove}
          onCancel={() => setRemoving(null)}
        />
      )}
    </section>
  )
}
