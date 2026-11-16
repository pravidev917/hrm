import axios from 'axios'

// Vite env vars are public (bundled into the browser). Never put secrets here.
const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

// The backend authenticates with an HttpOnly "HRM_AUTH" cookie, so the browser
// must send credentials. The JWT is never readable from JavaScript.
export const apiClient = axios.create({
  baseURL,
  withCredentials: true,
  timeout: 20000,
  headers: { 'Content-Type': 'application/json' },
})

let unauthorizedHandler = null
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    const url = error.config?.url ?? ''
    // 401 on a protected endpoint means the cookie expired or the account was disabled.
    if (status === 401 && !url.startsWith('/api/auth/') && unauthorizedHandler) {
      unauthorizedHandler()
    }
    return Promise.reject(error)
  },
)

export const unwrap = (request) => request.then((response) => response.data)

/** Turns any Axios error into a short, user-safe message. */
export function getErrorMessage(error, fallback = 'Something went wrong. Please try again.') {
  if (!error?.response) {
    return error?.code === 'ECONNABORTED'
      ? 'The server took too long to respond.'
      : 'Cannot reach the server. Check your connection and try again.'
  }
  const { status, data } = error.response
  if (status === 401) return data?.message || 'Your session has expired. Please sign in again.'
  if (status === 403) return data?.message || 'You do not have permission to do that.'
  if (status >= 500) return fallback
  if (typeof data?.message === 'string' && data.message.trim()) return data.message
  if (status === 400) return 'Please check the information you entered.'
  if (status === 404) return 'The requested item was not found.'
  return fallback
}
