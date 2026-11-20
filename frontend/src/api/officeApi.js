import { apiClient, unwrap } from './axiosClient'

export const adminOfficeApi = {
  get: () => unwrap(apiClient.get('/api/admin/office-location')),
  save: (body) => unwrap(apiClient.put('/api/admin/office-location', body)),
}

export const employeeOfficeApi = {
  get: () => unwrap(apiClient.get('/api/employee/office-location')),
}
