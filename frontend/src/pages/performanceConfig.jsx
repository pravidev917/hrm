import { performanceApi } from '../api/recordsApi'
import { clean, clamp } from './recordHelpers'

export const performanceConfig = {
  title: 'Performance',
  subtitle: 'Performance notes and ratings.',
  noun: 'Performance record',
  idKey: 'performanceId',
  api: performanceApi,
  searchKeys: ['dailyPerformance', 'weeklyPerformance'],
  fields: [
    { name: 'overallRating', label: 'Overall rating (0–5)', type: 'number', min: 0, max: 5, step: '0.01', half: true, hint: 'Up to 2 decimal places' },
    { name: 'dailyPerformance', label: 'Daily performance', type: 'textarea', maxLength: 5000 },
    { name: 'weeklyPerformance', label: 'Weekly performance', type: 'textarea', maxLength: 5000 },
  ],
  columns: [
    { key: 'overallRating', label: 'Rating', sortable: true, render: (r) => (r.overallRating == null ? '—' : Number(r.overallRating).toFixed(2)) },
    { key: 'dailyPerformance', label: 'Daily', render: (r) => clamp(r.dailyPerformance) },
    { key: 'weeklyPerformance', label: 'Weekly', render: (r) => clamp(r.weeklyPerformance) },
  ],
  validate: (v) => {
    const errors = {}
    if (!String(v.dailyPerformance ?? '').trim() && !String(v.weeklyPerformance ?? '').trim() && v.overallRating === '') errors.dailyPerformance = 'Fill in at least one field'
    if (v.overallRating !== '' && !/^\d+(\.\d{1,2})?$/.test(String(v.overallRating))) errors.overallRating = 'Use at most 2 decimal places'
    return errors
  },
  toPayload: (v) => ({ dailyPerformance: clean(v.dailyPerformance), weeklyPerformance: clean(v.weeklyPerformance), overallRating: v.overallRating === '' ? null : Number(v.overallRating) }),
}
