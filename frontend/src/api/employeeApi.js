import { apiClient, unwrap } from './axiosClient'

export const employeePortalApi = {
  profile: () => unwrap(apiClient.get('/api/employee/profile')),
  dashboard: () => unwrap(apiClient.get('/api/employee/dashboard')),
  // Active clients of the employee's own admin, for picking a client on a report.
  clients: () => unwrap(apiClient.get('/api/employee/clients')),
}
