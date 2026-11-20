import { useCallback, useEffect, useState } from 'react'
import { adminOfficeApi } from '../api/officeApi'
import { sourcingPlatformApi } from '../api/reportingApi'
import { getErrorMessage } from '../api/axiosClient'
import { useFetch } from '../hooks/useFetch'
import { useToast } from '../hooks/useToast'
import PageHeader from '../components/common/PageHeader'
import { Alert, ErrorState, Spinner } from '../components/common/Feedback'
import Icon from '../components/common/Icon'
import { getBrowserLocation } from '../utils/geolocation'

function browserTimezone() {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Kolkata'
  } catch {
    return 'Asia/Kolkata'
  }
}

function validate(values) {
  const errors = {}
  if (!values.officeName.trim()) errors.officeName = 'Office name is required'
  const lat = Number(values.latitude)
  const lon = Number(values.longitude)
  const radius = Number(values.radiusMeters)

  if (values.latitude === '' || Number.isNaN(lat)) errors.latitude = 'Enter a latitude'
  else if (lat < -90 || lat > 90) errors.latitude = 'Latitude must be between -90 and 90'

  if (values.longitude === '' || Number.isNaN(lon)) errors.longitude = 'Enter a longitude'
  else if (lon < -180 || lon > 180) errors.longitude = 'Longitude must be between -180 and 180'

  if (values.radiusMeters === '' || Number.isNaN(radius)) errors.radiusMeters = 'Enter a radius in metres'
  else if (radius < 10 || radius > 10000) errors.radiusMeters = 'Radius must be between 10 and 10000 metres'

  if (!values.timezone.trim()) errors.timezone = 'Timezone is required'
  return errors
}

function OfficeSettingsForm({ initial, onSaved }) {
  const toast = useToast()
  const [values, setValues] = useState(() => ({
    officeName: initial?.officeName ?? '',
    latitude: initial ? String(initial.latitude ?? '') : '',
    longitude: initial ? String(initial.longitude ?? '') : '',
    radiusMeters: String(initial?.radiusMeters ?? 300),
    timezone: initial?.timezone ?? browserTimezone(),
  }))
  const [errors, setErrors] = useState({})
  const [apiError, setApiError] = useState('')
  const [locating, setLocating] = useState(false)
  const [locationNote, setLocationNote] = useState('')
  const [saving, setSaving] = useState(false)

  function setValue(name, value) {
    setValues((v) => ({ ...v, [name]: value }))
    setErrors((e) => ({ ...e, [name]: undefined }))
    setApiError('')
  }

  async function useCurrentLocation() {
    setLocating(true)
    setApiError('')
    setLocationNote('')
    try {
      const position = await getBrowserLocation()
      setValues((v) => ({
        ...v,
        latitude: position.latitude.toFixed(6),
        longitude: position.longitude.toFixed(6),
      }))
      setErrors((e) => ({ ...e, latitude: undefined, longitude: undefined }))
      setLocationNote(`Coordinates filled from this device (accuracy about ${Math.round(position.accuracyMeters)} m).`)
    } catch (e) {
      setApiError(e.message)
    } finally {
      setLocating(false)
    }
  }

  async function save(event) {
    event.preventDefault()
    const nextErrors = validate(values)
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length) return

    setSaving(true)
    setApiError('')
    try {
      await adminOfficeApi.save({
        officeName: values.officeName.trim(),
        latitude: Number(values.latitude),
        longitude: Number(values.longitude),
        radiusMeters: Number(values.radiusMeters),
        timezone: values.timezone.trim(),
      })
      toast.success('Office settings saved')
      setLocationNote('')
      onSaved()
    } catch (e) {
      setApiError(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="card" onSubmit={save} noValidate>
      <div className="form-grid">
        <div className="field span-2">
          <label htmlFor="officeName">Office name</label>
          <input
            id="officeName"
            value={values.officeName}
            maxLength={150}
            onChange={(e) => setValue('officeName', e.target.value)}
            aria-invalid={Boolean(errors.officeName)}
            autoComplete="organization"
          />
          {errors.officeName && <span className="field-error">{errors.officeName}</span>}
        </div>

        <div className="field">
          <label htmlFor="latitude">Latitude</label>
          <input
            id="latitude"
            type="number"
            step="0.000001"
            min="-90"
            max="90"
            inputMode="decimal"
            value={values.latitude}
            onChange={(e) => setValue('latitude', e.target.value)}
            aria-invalid={Boolean(errors.latitude)}
          />
          {errors.latitude && <span className="field-error">{errors.latitude}</span>}
        </div>

        <div className="field">
          <label htmlFor="longitude">Longitude</label>
          <input
            id="longitude"
            type="number"
            step="0.000001"
            min="-180"
            max="180"
            inputMode="decimal"
            value={values.longitude}
            onChange={(e) => setValue('longitude', e.target.value)}
            aria-invalid={Boolean(errors.longitude)}
          />
          {errors.longitude && <span className="field-error">{errors.longitude}</span>}
        </div>

        <div className="field">
          <label htmlFor="radiusMeters">Allowed radius (metres)</label>
          <input
            id="radiusMeters"
            type="number"
            step="1"
            min="10"
            max="10000"
            inputMode="numeric"
            value={values.radiusMeters}
            onChange={(e) => setValue('radiusMeters', e.target.value)}
            aria-invalid={Boolean(errors.radiusMeters)}
          />
          {errors.radiusMeters ? (
            <span className="field-error">{errors.radiusMeters}</span>
          ) : (
            <span className="field-hint">
              Attendance is accepted anywhere inside this radius; exact coordinates are not required.
            </span>
          )}
        </div>

        <div className="field">
          <label htmlFor="timezone">Attendance timezone</label>
          <input
            id="timezone"
            value={values.timezone}
            maxLength={64}
            onChange={(e) => setValue('timezone', e.target.value)}
            aria-invalid={Boolean(errors.timezone)}
            placeholder="Asia/Kolkata"
          />
          {errors.timezone ? (
            <span className="field-error">{errors.timezone}</span>
          ) : (
            <span className="field-hint">IANA zone used for the attendance date near midnight.</span>
          )}
        </div>

        <div className="span-2">
          <div className="row-actions">
            <button type="button" className="btn btn-secondary" onClick={useCurrentLocation} disabled={locating}>
              <Icon name="pin" size={16} /> {locating ? 'Checking location…' : 'Use my current location'}
            </button>
            <span className="muted small">
              Manual coordinates work too. Browser location needs HTTPS or localhost.
            </span>
          </div>
          {locationNote && <Alert type="success">{locationNote}</Alert>}
        </div>

        {apiError && (
          <div className="span-2">
            <Alert>{apiError}</Alert>
          </div>
        )}

        {initial?.updatedAt && (
          <div className="span-2 muted small">
            Last changed {initial.updatedByName ? `by ${initial.updatedByName} ` : ''}
            {new Date(initial.updatedAt).toLocaleString()}.
          </div>
        )}

        <div className="span-2 row-actions">
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? 'Saving…' : 'Save office settings'}
          </button>
        </div>
      </div>
    </form>
  )
}

function SourcingPlatformsManager() {
  const toast = useToast()
  const [platforms, setPlatforms] = useState([])
  const [name, setName] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    sourcingPlatformApi
      .admin()
      .then(setPlatforms)
      .catch((e) => setError(getErrorMessage(e)))
  }, [])

  useEffect(load, [load])

  async function add(event) {
    event.preventDefault()
    if (!name.trim()) return
    setBusy(true)
    setError('')
    try {
      await sourcingPlatformApi.create({ name: name.trim(), active: true })
      setName('')
      toast.success('Sourcing platform added')
      load()
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  async function toggle(platform) {
    setBusy(true)
    setError('')
    try {
      await sourcingPlatformApi.update(platform.sourcingPlatformId, { active: !platform.active })
      load()
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card" style={{ marginTop: 'var(--space-4)' }}>
      <div className="card-header">
        <div>
          <h2>Sourcing platforms</h2>
          <p className="muted small">
            Options offered on daily reports and candidate tracking. Renaming or disabling one never changes
            old records; historical blank platforms stay Unknown.
          </p>
        </div>
      </div>

      {error && <Alert>{error}</Alert>}

      <form className="row-actions" onSubmit={add} style={{ marginBottom: 'var(--space-3)' }}>
        <label className="filter" style={{ flex: 1 }}>
          <span className="sr-only">New sourcing platform</span>
          <input
            value={name}
            maxLength={80}
            placeholder="Add a platform, e.g. Hirist"
            aria-label="New sourcing platform"
            onChange={(e) => setName(e.target.value)}
          />
        </label>
        <button type="submit" className="btn btn-primary" disabled={busy || !name.trim()}>
          Add platform
        </button>
      </form>

      <ul className="list-plain">
        {platforms.map((platform) => (
          <li key={platform.sourcingPlatformId} className="note-head">
            <span>
              {platform.name} {!platform.active && <span className="badge badge-gray">Inactive</span>}
            </span>
            <button
              type="button"
              className="btn btn-sm"
              disabled={busy}
              onClick={() => toggle(platform)}
            >
              {platform.active ? 'Disable' : 'Enable'}
            </button>
          </li>
        ))}
        {platforms.length === 0 && <li className="muted small">No platforms configured yet.</li>}
      </ul>
    </section>
  )
}

export default function OfficeSettingsPage() {
  const fetcher = useCallback(() => adminOfficeApi.get(), [])
  const { data, loading, error, reload } = useFetch(fetcher)

  if (loading) return <Spinner />

  return (
    <>
      <PageHeader
        title="Office settings"
        subtitle="Employees marking office attendance must be within this area. All administrators of your company share these settings."
      />

      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : (
        <>
          <OfficeSettingsForm
            key={data?.officeLocationId ?? 'new'}
            initial={data?.officeName ? data : null}
            onSaved={reload}
          />
          <SourcingPlatformsManager />
        </>
      )}
    </>
  )
}
