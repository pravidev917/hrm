import { useState } from 'react'
import { Link } from 'react-router-dom'
import { authApi } from '../api/authApi'
import { getErrorMessage } from '../api/axiosClient'
import AuthShell from '../components/common/AuthShell'
import { Alert } from '../components/common/Feedback'
import { Field } from '../components/common/FormModal'

const COMMON = [
  { name: 'email', label: 'Email', type: 'email', required: true, autoComplete: 'email' },
  { name: 'password', label: 'Password', type: 'password', required: true, minLength: 8, hint: 'At least 8 characters', autoComplete: 'new-password' },
]

const FORMS = {
  EMPLOYEE: {
    submit: (v) =>
      authApi.registerEmployee({
        adminId: Number(v.adminId),
        employeeName: v.employeeName.trim(),
        email: v.email.trim(),
        password: v.password,
        role: v.role.trim(),
        contact: v.contact.trim() || null,
      }),
    fields: [
      { name: 'employeeName', label: 'Full name', required: true, half: true, autoComplete: 'name' },
      { name: 'role', label: 'Job designation', required: true, half: true, placeholder: 'e.g. Recruiter' },
      COMMON[0],
      COMMON[1],
      { name: 'contact', label: 'Phone', half: true, maxLength: 50, autoComplete: 'tel' },
      { name: 'adminId', label: 'Admin ID', type: 'number', min: 1, required: true, hint: 'Ask your administrator for their ID' },
    ],
    done: 'Account created. An administrator must approve it before you can sign in.',
  },
  ADMIN: {
    submit: (v) =>
      authApi.registerAdmin({
        adminName: v.adminName.trim(),
        email: v.email.trim(),
        password: v.password,
        contactDetails: v.contactDetails.trim() || null,
      }),
    fields: [
      { name: 'adminName', label: 'Full name', required: true, half: true },
      { name: 'contactDetails', label: 'Phone', half: true, maxLength: 50 },
      COMMON[0],
      COMMON[1],
    ],
    done: 'Administrator account created. You can sign in now.',
  },
}

export default function RegisterPage() {
  const [kind, setKind] = useState('EMPLOYEE')
  return <RegisterForm key={kind} kind={kind} onKind={setKind} />
}

function RegisterForm({ kind, onKind }) {
  const form = FORMS[kind]
  const [values, setValues] = useState(() => Object.fromEntries(form.fields.map((f) => [f.name, ''])))
  const [errors, setErrors] = useState({})
  const [apiError, setApiError] = useState('')
  const [success, setSuccess] = useState('')
  const [submitting, setSubmitting] = useState(false)

  function validate() {
    const next = {}
    for (const f of form.fields) {
      const v = String(values[f.name] ?? '').trim()
      if (f.required && !v) next[f.name] = `${f.label} is required`
      else if (v && f.type === 'email' && !/^\S+@\S+\.\S+$/.test(v)) next[f.name] = 'Enter a valid email'
      else if (v && f.minLength && v.length < f.minLength) next[f.name] = `${f.label} must be at least ${f.minLength} characters`
      else if (v && f.type === 'number' && !(Number(v) >= f.min)) next[f.name] = `${f.label} must be a positive number`
    }
    setErrors(next)
    return Object.keys(next).length === 0
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setApiError('')
    if (!validate()) return
    setSubmitting(true)
    try {
      await form.submit(values)
      setSuccess(form.done)
    } catch (error) {
      setApiError(getErrorMessage(error, 'Registration failed. Please try again.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell
      title="Create account"
      subtitle={kind === 'EMPLOYEE' ? 'Register as an employee.' : 'Set up the first administrator account.'}
      footer={
        <>
          Already registered? <Link to="/login">Sign in</Link>
        </>
      }
    >
      <div className="segmented" role="tablist" aria-label="Account type">
        {[
          ['EMPLOYEE', 'Employee'],
          ['ADMIN', 'Administrator'],
        ].map(([value, label]) => (
          <button key={value} type="button" role="tab" aria-selected={kind === value} className={kind === value ? 'seg active' : 'seg'} onClick={() => onKind(value)}>
            {label}
          </button>
        ))}
      </div>
      {success ? (
        <Alert type="success">{success}</Alert>
      ) : (
        <form onSubmit={handleSubmit} noValidate className="form-grid">
          {apiError && (
            <div className="span-2">
              <Alert>{apiError}</Alert>
            </div>
          )}
          {form.fields.map((f) => (
            <Field key={f.name} field={f} value={values[f.name]} error={errors[f.name]} onChange={(v) => setValues((s) => ({ ...s, [f.name]: v }))} />
          ))}
          <div className="span-2">
            <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
              {submitting ? 'Creating account…' : 'Create account'}
            </button>
          </div>
          {kind === 'ADMIN' && <p className="muted small span-2">Administrator sign-up is only available until the first administrator exists.</p>}
        </form>
      )}
    </AuthShell>
  )
}
