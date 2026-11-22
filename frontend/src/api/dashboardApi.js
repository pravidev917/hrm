import { apiClient, unwrap } from './axiosClient'

export const adminDashboardApi = {
  // Aggregated, server-computed dashboard summary (no client-side counting).
  summary: () => unwrap(apiClient.get('/api/admin/dashboard')),
}

/**
 * Sourcing analytics counted from daily reports: profiles per employee and per
 * platform, over the range "first daily report -> today" (computed server-side, so
 * the table and both charts always agree with the reports they come from).
 */
export const sourcingAnalyticsApi = {
  summary: () => unwrap(apiClient.get('/api/admin/sourcing')),
}
