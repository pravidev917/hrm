import { apiClient, unwrap } from './axiosClient'

export const employeeWfhApi = {
  list: () => unwrap(apiClient.get('/api/employee/wfh')),
  today: () => unwrap(apiClient.get('/api/employee/wfh/today')),
  apply: (body) => unwrap(apiClient.post('/api/employee/wfh', body)),
  cancel: (id) => unwrap(apiClient.patch(`/api/employee/wfh/${id}/cancel`)),
}

export const adminWfhApi = {
  list: () => unwrap(apiClient.get('/api/admin/wfh')),
  // body: { status: 'APPROVED' | 'REJECTED', comment }
  review: (id, body) => unwrap(apiClient.patch(`/api/admin/wfh/${id}/review`, body)),
  revoke: (id, comment) => unwrap(apiClient.patch(`/api/admin/wfh/${id}/revoke`, { comment: comment || null })),
}
