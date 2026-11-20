import { apiClient, unwrap } from './axiosClient'

/**
 * Performance trackers.
 *
 * - adminMonthly(month): the admin tracker for one calendar month ('YYYY-MM',
 *   defaults to the current month): daily-resume leaderboard + revenue report.
 * - employee(date): the signed-in employee's own daily target, monthly revenue
 *   target and same-day comparison ('YYYY-MM-DD', defaults to today).
 */
export const trackerApi = {
  adminMonthly: (month) =>
    unwrap(apiClient.get('/api/admin/performance-tracker/monthly', { params: month ? { month } : undefined })),
  employee: (date) =>
    unwrap(apiClient.get('/api/employee/performance-tracker', { params: date ? { date } : undefined })),
}
