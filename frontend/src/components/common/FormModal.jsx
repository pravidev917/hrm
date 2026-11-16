import { useId, useState } from 'react'
import Modal from './Modal'
import { Alert } from './Feedback'
import { getErrorMessage } from '../../api/axiosClient'

/**
 * Config-driven form in a modal.
 * field: { name, label, type, required, maxLength, minLength, min, max, step, options, placeholder, hint, half, validate }
 * hint(values) / intro: optional computed guidance shown above the fields.
 * onSubmit(values) must return a promise; rejections are shown inside the form.
 */
export default function FormModal({
  title,
  fields,
  initialValues = {},
  submitLabel = 'Save',
  onSubmit,
  onClose,
  validate,
  hint,
  intro,
  wide,
}) {
  const formId = useId()
  const [values, setValues] = useState(() =>
    Object.fromEntries(fields.map((f) => [f.name, initialValues[f.name] ?? ''])),
  )
  const [errors, setErrors] = useState({})
  const [apiError, setApiError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const setValue = (name, value) => {
    setValues((v) => ({ ...v, [name]: value }))
    setErrors((e) => ({ ...e, [name]: undefined }))
  }

  function validateAll() {
    const next = {}
    for (const f of fields) {
      const raw = values[f.name]
      const value = typeof raw === 'string' ? raw.trim() : raw
      if (f.required && (value === '' || value == null)) {
        next[f.name] = `${f.label} is required`
        continue
      }
      if (value === '' || value == null) continue
      if (f.maxLength && String(value).length > f.maxLength) next[f.name] = `${f.label} must be at most ${f.maxLength} characters`
      else if (f.type === 'number') {
        const n = Number(value)
        if (Number.isNaN(n)) next[f.name] = `${f.label} must be a number`
        else if (f.min != null && n < f.min) next[f.name] = `${f.label} must be at least ${f.min}`
        else if (f.max != null && n > f.max) next[f.name] = `${f.label} must be at most ${f.max}`
      } else if (f.type === 'email' && !/^\S+@\S+\.\S+$/.test(value)) next[f.name] = 'Enter a valid email'
      else if (f.minLength && String(value).length < f.minLength) next[f.name] = `${f.label} must be at least ${f.minLength} characters`
    }
    if (Object.keys(next).length === 0 && validate) Object.assign(next, validate(values) ?? {})
    setErrors(next)
    return Object.keys(next).length === 0
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setApiError('')
    if (!validateAll()) return
    setSubmitting(true)
    try {
      await onSubmit(values)
    } catch (error) {
      setApiError(getErrorMessage(error))
      setSubmitting(false)
    }
  }

  return (
    <Modal
      title={title}
      wide={wide}
      onClose={submitting ? () => {} : onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" form={formId} className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Saving…' : submitLabel}
          </button>
        </>
      }
    >
      <form id={formId} onSubmit={handleSubmit} noValidate className="form-grid">
        {apiError && (
          <div className="span-2">
            <Alert>{apiError}</Alert>
          </div>
        )}
        {intro && <div className="span-2">{intro}</div>}
        {hint && hint(values) && (
          <div className="span-2">
            <Alert type="info">{hint(values)}</Alert>
          </div>
        )}
        {fields.map((f) => (
          <Field key={f.name} field={f} value={values[f.name]} error={errors[f.name]} onChange={(v) => setValue(f.name, v)} />
        ))}
      </form>
    </Modal>
  )
}

export function Field({ field: f, value, error, onChange }) {
  const id = useId()
  const describedBy = error ? `${id}-err` : f.hint ? `${id}-hint` : undefined
  const common = {
    id,
    name: f.name,
    value: value ?? '',
    required: f.required,
    'aria-invalid': error ? true : undefined,
    'aria-describedby': describedBy,
    onChange: (e) => onChange(e.target.value),
  }
  let control
  if (f.type === 'select') {
    control = (
      <select {...common}>
        <option value="">{f.placeholder ?? 'Select…'}</option>
        {f.options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    )
  } else if (f.type === 'textarea') {
    control = <textarea rows={3} maxLength={f.maxLength} placeholder={f.placeholder} {...common} />
  } else {
    control = (
      <input
        type={f.type ?? 'text'}
        maxLength={f.maxLength}
        min={f.min}
        max={f.max}
        step={f.step}
        placeholder={f.placeholder}
        autoComplete={f.autoComplete}
        {...common}
      />
    )
  }
  return (
    <div className={f.half ? 'field' : 'field span-2'}>
      <label htmlFor={id}>
        {f.label}
        {f.required && <span className="req" aria-hidden="true"> *</span>}
      </label>
      {control}
      {error ? (
        <span id={`${id}-err`} className="field-error">
          {error}
        </span>
      ) : (
        f.hint && (
          <span id={`${id}-hint`} className="muted small">
            {f.hint}
          </span>
        )
      )}
    </div>
  )
}
