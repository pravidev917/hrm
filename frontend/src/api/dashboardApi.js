import { apiClient, unwrap } from './axiosClient'

export const adminDashboardApi = {
  // Aggregated, server-computed dashboard summary (no client-side counting).
  summary: () => unwrap(apiClient.get('/api/admin/dashboard')),
}
