import { apiClient, unwrap } from './axiosClient'

export const calendarApi = {
  // month: 'YYYY-MM'. Returns birthdays, anniversaries, holidays and achievements.
  events: (month) => unwrap(apiClient.get('/api/calendar/events', { params: { month } })),
}
