/**
 * Browser geolocation for attendance.
 *
 * Rules:
 *  - only called when the employee explicitly marks office attendance
 *  - a fresh, high-accuracy fix is requested (maximumAge: 0)
 *  - nothing is stored in the browser and no continuous tracking is used
 *
 * Browser geolocation needs a secure context: HTTPS, or localhost during
 * development. A position reported by a browser can be spoofed by a determined
 * user; this is a policy/verification aid, not proof of physical presence.
 */

export const LOCATION_TIMEOUT_MS = 15000

export class GeolocationError extends Error {}

export function geolocationSupported() {
  return typeof navigator !== 'undefined' && Boolean(navigator.geolocation)
}

export function secureContext() {
  return typeof window !== 'undefined' && (window.isSecureContext || window.location.hostname === 'localhost')
}

/** Resolve a fresh position: { latitude, longitude, accuracyMeters, capturedAtEpochMillis }. */
export function getBrowserLocation() {
  if (!geolocationSupported()) {
    return Promise.reject(
      new GeolocationError('This browser cannot provide a location. Use a browser with location support, or ask your administrator.'),
    )
  }

  if (!secureContext()) {
    return Promise.reject(
      new GeolocationError('Location requires HTTPS or localhost. Open the app over HTTPS to mark office attendance.'),
    )
  }

  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const { latitude, longitude, accuracy } = position.coords
        resolve({
          latitude,
          longitude,
          accuracyMeters: accuracy,
          capturedAtEpochMillis: position.timestamp || Date.now(),
        })
      },
      (error) => reject(new GeolocationError(geolocationErrorMessage(error))),
      { enableHighAccuracy: true, timeout: LOCATION_TIMEOUT_MS, maximumAge: 0 },
    )
  })
}

export function geolocationErrorMessage(error) {
  switch (error?.code) {
    case 1:
      return 'Location permission was denied. Allow location access for this site in your browser settings, then try again.'
    case 2:
      return 'Your location is unavailable right now. Move to an open area or check that location services are on, then try again.'
    case 3:
      return 'Getting your location timed out. Move to an open area and try again.'
    default:
      return 'Could not get your location. Please try again.'
  }
}
