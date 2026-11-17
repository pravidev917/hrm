import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { getErrorMessage } from '../api/axiosClient'
import AuthShell from '../components/common/AuthShell'
import { Alert } from '../components/common/Feedback'
import Icon from '../components/common/Icon'

const EMAIL_RE = /^\S+@\S+\.\S+$/

export default function LoginPage() {
  const { user, initializing, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [role, setRole] = useState('EMPLOYEE')
  const [values, setValues] = useState({ identifier: '', password: '' })
  const [errors, setErrors] = useState({})
  const [apiError, setApiError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [showPassword, setShowPassword] = useState(false)

  if (!initializing && user) return <Navigate to="/dashboard" replace />

  const isAdmin = role === 'ADMIN'

  async function handleSubmit(e) {
    e.preventDefault()
    setApiError('')
    const next = {}
    const identifier = values.identifier.trim()
    if (!identifier) next.identifier = isAdmin ? 'Email is required' : 'Enter your email, phone or employee ID'
    // Administrators sign in by email only; employees may use phone or employee ID.
    else if (isAdmin && !EMAIL_RE.test(identifier)) next.identifier = 'Enter a valid email address'
    if (!values.password) next.password = 'Password is required'
    setErrors(next)
    if (Object.keys(next).length) return

    setSubmitting(true)
    try {
      await login(role, { identifier, password: values.password })
      // Only follow same-app paths for the post-login redirect.
      const from = location.state?.from
      navigate(typeof from === 'string' && from.startsWith('/') && !from.startsWith('//') ? from : '/dashboard', {
        replace: true,
      })
    } catch (error) {
      setApiError(getErrorMessage(error, 'Sign in failed. Please try again.'))
      setSubmitting(false)
    }
  }

  return (
    <AuthShell
      title="Sign in"
      subtitle="Choose your account type to continue."
      footer={<span>Need an account? Ask your administrator to create one.</span>}
    >
      <div className="segmented" role="tablist" aria-label="Account type">
        {[
          ['EMPLOYEE', 'Employee'],
          ['ADMIN', 'Administrator'],
        ].map(([value, label]) => (
          <button
            key={value}
            type="button"
            role="tab"
            aria-selected={role === value}
            className={role === value ? 'seg active' : 'seg'}
            onClick={() => {
              setRole(value)
              setApiError('')
              setErrors({})
            }}
          >
            {label}
          </button>
        ))}
      </div>

      <form onSubmit={handleSubmit} noValidate>
        {apiError && <Alert>{apiError}</Alert>}

        <div className="field" style={{ marginBottom: 14 }}>
          <label htmlFor="login-identifier">
            {isAdmin ? 'Email' : 'Email, phone or employee ID'} <span className="req" aria-hidden="true">*</span>
          </label>
          <input
            id="login-identifier"
            name="identifier"
            type={isAdmin ? 'email' : 'text'}
            autoComplete="username"
            value={values.identifier}
            onChange={(e) => {
              setValues((s) => ({ ...s, identifier: e.target.value }))
              setErrors((s) => ({ ...s, identifier: undefined }))
            }}
            aria-invalid={errors.identifier ? true : undefined}
            aria-describedby={errors.identifier ? 'login-identifier-err' : undefined}
            placeholder={isAdmin ? 'you@company.com' : 'you@company.com · 98765 43210 · EMP-0007'}
          />
          {errors.identifier && (
            <span id="login-identifier-err" className="field-error">
              {errors.identifier}
            </span>
          )}
        </div>

        <div className="field" style={{ marginBottom: 20 }}>
          <label htmlFor="login-password">
            Password <span className="req" aria-hidden="true">*</span>
          </label>
          <div style={{ position: 'relative' }}>
            <input
              id="login-password"
              name="password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              value={values.password}
              onChange={(e) => {
                setValues((s) => ({ ...s, password: e.target.value }))
                setErrors((s) => ({ ...s, password: undefined }))
              }}
              aria-invalid={errors.password ? true : undefined}
              aria-describedby={errors.password ? 'login-password-err' : undefined}
              placeholder="Your password"
              style={{ paddingRight: 44 }}
            />
            <button
              type="button"
              className="icon-btn"
              style={{ position: 'absolute', right: 2, top: 2 }}
              aria-label={showPassword ? 'Hide password' : 'Show password'}
              onClick={() => setShowPassword((v) => !v)}
            >
              <Icon name="shield" size={16} />
            </button>
          </div>
          {errors.password && (
            <span id="login-password-err" className="field-error">
              {errors.password}
            </span>
          )}
        </div>

        <button type="submit" className="btn btn-primary btn-block btn-lg" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </AuthShell>
  )
}
