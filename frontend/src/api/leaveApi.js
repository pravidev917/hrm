import { apiClient, unwrap } from './axiosClient'

export const employeeLeaveApi = {
  list: () => unwrap(apiClient.get('/api/employee/leaves')),
  apply: (body) => unwrap(apiClient.post('/api/employee/leaves', body)),
  update: (id, body) => unwrap(apiClient.put(`/api/employee/leaves/${id}`, body)),
  cancel: (id) => unwrap(apiClient.patch(`/api/employee/leaves/${id}/cancel`)),
}

export const adminLeaveApi = {
  list: () => unwrap(apiClient.get('/api/admin/leaves')),
  // body: { status: 'APPROVED' | 'REJECTED', remarks }
  review: (id, body) => unwrap(apiClient.patch(`/api/admin/leaves/${id}/review`, body)),
}
