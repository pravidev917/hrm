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

export const adminAttendanceApi = {
  list: () => unwrap(apiClient.get('/api/admin/attendance')),
  update: (id, status, workMode) =>
    unwrap(apiClient.put(`/api/admin/attendance/${id}`, null, { params: { status, workMode } })),
  remove: (id) => unwrap(apiClient.delete(`/api/admin/attendance/${id}`)),
}
