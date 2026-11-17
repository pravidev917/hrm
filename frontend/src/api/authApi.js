import { apiClient, unwrap } from './axiosClient'

// There is no self-registration: administrators create employee accounts
// (see adminApi.adminEmployeeApi.create). Logins only.
export const authApi = {
  loginAdmin: (body) => unwrap(apiClient.post('/api/auth/admin/login', body)),
  loginEmployee: (body) => unwrap(apiClient.post('/api/auth/employee/login', body)),
  logout: () => unwrap(apiClient.post('/api/auth/logout')),
  // -> { email, authorities: [{ authority: 'ROLE_ADMIN' }] }
  me: () => unwrap(apiClient.get('/api/auth/me')),
}
