import { apiClient, unwrap } from './axiosClient'

export const employeeAttendanceApi = {
  list: () => unwrap(apiClient.get('/api/employee/attendance')),
  // status is a query parameter: PRESENT | ABSENT.
  // body carries location evidence for office PRESENT and is omitted for ABSENT/WFH.
  mark: (status, location) =>
    unwrap(apiClient.post('/api/employee/attendance', location ?? null, { params: { status } })),
  update: (id, status, location) =>
    unwrap(apiClient.put(`/api/employee/attendance/${id}`, location ?? null, { params: { status } })),
}

/**
 * Login / logout attendance sessions.
 *
 * No employee id is sent: the server identifies the employee from the session cookie.
 * Attendance logout closes the working session only; it does not sign the user out.
 */
export const employeeSessionApi = {
  today: () => unwrap(apiClient.get('/api/employee/attendance/today')),
  login: () => unwrap(apiClient.post('/api/employee/attendance/login')),
  logout: () => unwrap(apiClient.post('/api/employee/attendance/logout')),
  history: (params) => unwrap(apiClient.get('/api/employee/attendance/history', { params })),
}

export const adminAttendanceApi = {
  list: () => unwrap(apiClient.get('/api/admin/attendance/records')),
  update: (id, status, workMode) =>
    unwrap(apiClient.put(`/api/admin/attendance/${id}`, null, { params: { status, workMode } })),
  remove: (id) => unwrap(apiClient.delete(`/api/admin/attendance/${id}`)),
}

/** Administrator attendance monitor: rows, who is logged in, history, totals, export. */
export const adminAttendanceMonitorApi = {
  rows: (params) => unwrap(apiClient.get('/api/admin/attendance', { params })),
  active: () => unwrap(apiClient.get('/api/admin/attendance/active')),
  employeeHistory: (employeeId, params) =>
    unwrap(apiClient.get(`/api/admin/attendance/employee/${employeeId}`, { params })),
  summary: (params) => unwrap(apiClient.get('/api/admin/attendance/summary', { params })),
  // The export needs the auth cookie, so it is fetched as a blob and saved by the browser.
  exportCsv: (params) =>
    apiClient.get('/api/admin/attendance/export', { params, responseType: 'blob' }).then((response) => response.data),
}
