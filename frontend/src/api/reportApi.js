import { apiClient, unwrap } from './axiosClient'

export const employeeDailyReportApi = {
  list: () => unwrap(apiClient.get('/api/employee/reports')),
  create: (clientId, body) =>
    unwrap(apiClient.post('/api/employee/reports', body, { params: { clientId } })),
  update: (id, clientId, body) =>
    unwrap(
      apiClient.put(`/api/employee/reports/${id}`, body, {
        params: clientId ? { clientId } : undefined,
      }),
    ),
  remove: (id) => unwrap(apiClient.delete(`/api/employee/reports/${id}`)),
}

export const adminDailyReportApi = {
  list: () => unwrap(apiClient.get('/api/admin/reports')),
}

export const employeeWeeklyReportApi = {
  list: () => unwrap(apiClient.get('/api/employee/weekly-reports')),
  create: (body) => unwrap(apiClient.post('/api/employee/weekly-reports', body)),
  update: (id, body) => unwrap(apiClient.put(`/api/employee/weekly-reports/${id}`, body)),
  remove: (id) => unwrap(apiClient.delete(`/api/employee/weekly-reports/${id}`)),
}

export const adminWeeklyReportApi = {
  list: () => unwrap(apiClient.get('/api/admin/weekly-reports')),
  update: (id, body) => unwrap(apiClient.put(`/api/admin/weekly-reports/${id}`, body)),
  remove: (id) => unwrap(apiClient.delete(`/api/admin/weekly-reports/${id}`)),
}
