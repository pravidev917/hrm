import { apiClient, unwrap } from './axiosClient'

export const adminEmployeeApi = {
  list: () => unwrap(apiClient.get('/api/admin/employees')),
  create: (body) => unwrap(apiClient.post('/api/admin/employees', body)),
  update: (id, body) => unwrap(apiClient.put(`/api/admin/employees/${id}`, body)),
  remove: (id) => unwrap(apiClient.delete(`/api/admin/employees/${id}`)),
  activate: (id) => unwrap(apiClient.patch(`/api/admin/employees/${id}/activate`)),
  deactivate: (id) => unwrap(apiClient.patch(`/api/admin/employees/${id}/deactivate`)),
  // Server-side paged search: { search, status, page, size, sort }
  page: (params) => unwrap(apiClient.get('/api/admin/employees/page', { params })),
}

export const administratorApi = {
  list: () => unwrap(apiClient.get('/api/admin/admins')),
  get: (id) => unwrap(apiClient.get(`/api/admin/admins/${id}`)),
  create: (body) => unwrap(apiClient.post('/api/admin/admins', body)),
  update: (id, body) => unwrap(apiClient.put(`/api/admin/admins/${id}`, body)),
  remove: (id) => unwrap(apiClient.delete(`/api/admin/admins/${id}`)),
  activate: (id) => unwrap(apiClient.patch(`/api/admin/admins/${id}/activate`)),
  deactivate: (id) => unwrap(apiClient.patch(`/api/admin/admins/${id}/deactivate`)),
}

export const clientApi = {
  list: () => unwrap(apiClient.get('/api/admin/clients')),
  create: (body) => unwrap(apiClient.post('/api/admin/clients', body)),
  update: (id, body) => unwrap(apiClient.put(`/api/admin/clients/${id}`, body)),
  remove: (id) => unwrap(apiClient.delete(`/api/admin/clients/${id}`)),
  activate: (id) => unwrap(apiClient.patch(`/api/admin/clients/${id}/activate`)),
  deactivate: (id) => unwrap(apiClient.patch(`/api/admin/clients/${id}/deactivate`)),
}
