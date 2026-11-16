import { apiClient, unwrap } from './axiosClient'

export const authApi = {
  loginAdmin: (body) => unwrap(apiClient.post('/api/auth/admin/login', body)),
  loginEmployee: (body) => unwrap(apiClient.post('/api/auth/employee/login', body)),
  registerAdmin: (body) => unwrap(apiClient.post('/api/auth/admin/register', body)),
  registerEmployee: (body) => unwrap(apiClient.post('/api/auth/employee/register', body)),
  logout: () => unwrap(apiClient.post('/api/auth/logout')),
  // -> { email, authorities: [{ authority: 'ROLE_ADMIN' }] }
  me: () => unwrap(apiClient.get('/api/auth/me')),
}
