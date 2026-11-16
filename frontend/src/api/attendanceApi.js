import { apiClient, unwrap } from './axiosClient'

export const employeeAttendanceApi = {
  list: () => unwrap(apiClient.get('/api/employee/attendance')),
  // status is a query parameter: PRESENT | ABSENT
  mark: (status) => unwrap(apiClient.post('/api/employee/attendance', null, { params: { status } })),
  update: (id, status) =>
    unwrap(apiClient.put(`/api/employee/attendance/${id}`, null, { params: { status } })),
}

export const adminAttendanceApi = {
  list: () => unwrap(apiClient.get('/api/admin/attendance')),
  update: (id, status) =>
    unwrap(apiClient.put(`/api/admin/attendance/${id}`, null, { params: { status } })),
  remove: (id) => unwrap(apiClient.delete(`/api/admin/attendance/${id}`)),
}
