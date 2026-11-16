import { apiClient, unwrap } from './axiosClient'

// Feedback, follow-ups and performance share the same URL shape.
// Employees manage their own records; admins create records for an employee.
function makeEmployeeApi(base) {
  return {
    list: () => unwrap(apiClient.get(base)),
    create: (body) => unwrap(apiClient.post(base, body)),
    update: (id, body) => unwrap(apiClient.put(`${base}/${id}`, body)),
    remove: (id) => unwrap(apiClient.delete(`${base}/${id}`)),
  }
}

function makeAdminApi(base) {
  return {
    list: () => unwrap(apiClient.get(base)),
    create: (body, employeeId) => unwrap(apiClient.post(`${base}/employees/${employeeId}`, body)),
    update: (id, body) => unwrap(apiClient.put(`${base}/${id}`, body)),
    remove: (id) => unwrap(apiClient.delete(`${base}/${id}`)),
  }
}

export const feedbackApi = {
  employee: makeEmployeeApi('/api/employee/feedback'),
  admin: makeAdminApi('/api/admin/feedback'),
}
export const followupApi = {
  employee: makeEmployeeApi('/api/employee/followups'),
  admin: makeAdminApi('/api/admin/followups'),
}
export const performanceApi = {
  employee: makeEmployeeApi('/api/employee/performance'),
  admin: makeAdminApi('/api/admin/performance'),
  // Team leaderboard, ranked within each job designation.
  // period: 'YYYY-MM' or 'all'. Both roles are allowed; the backend scopes the
  // result to the caller's admin, so the URL is the same for everyone.
  leaderboard: (period) => unwrap(apiClient.get('/api/performance/leaderboard', { params: { period } })),
}
