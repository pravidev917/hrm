import { apiClient, unwrap } from './axiosClient'

export const reportingApi = {
  admin: (params) => unwrap(apiClient.get('/api/admin/reporting', { params })),
  employee: (params) => unwrap(apiClient.get('/api/employee/reporting', { params })),
}

export const sourcingPlatformApi = {
  admin: () => unwrap(apiClient.get('/api/admin/sourcing-platforms')),
  employee: () => unwrap(apiClient.get('/api/employee/sourcing-platforms')),
  create: (body) => unwrap(apiClient.post('/api/admin/sourcing-platforms', body)),
  update: (id, body) => unwrap(apiClient.put(`/api/admin/sourcing-platforms/${id}`, body)),
}
