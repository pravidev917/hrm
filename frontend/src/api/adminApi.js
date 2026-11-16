import { apiClient, unwrap } from './axiosClient'

export const adminEmployeeApi = {
  list: () => unwrap(apiClient.get('/api/admin/employees')),
  update: (id, body) => unwrap(apiClient.put(`/api/admin/employees/${id}`, body)),
  approve: (id) => unwrap(apiClient.put(`/api/admin/employees/${id}/approve`)),
  reject: (id) => unwrap(apiClient.put(`/api/admin/employees/${id}/reject`)),
  activate: (id) => unwrap(apiClient.patch(`/api/admin/employees/${id}/activate`)),
  deactivate: (id) => unwrap(apiClient.patch(`/api/admin/employees/${id}/deactivate`)),
  // Server-side paged search: { search, status, page, size, sort }
  page: (params) => unwrap(apiClient.get('/api/admin/employees/page', { params })),
}

export const clientApi = {
  list: () => unwrap(apiClient.get('/api/admin/clients')),
  create: (body) => unwrap(apiClient.post('/api/admin/clients', body)),
  update: (id, body) => unwrap(apiClient.put(`/api/admin/clients/${id}`, body)),
  activate: (id) => unwrap(apiClient.patch(`/api/admin/clients/${id}/activate`)),
  deactivate: (id) => unwrap(apiClient.patch(`/api/admin/clients/${id}/deactivate`)),
}
