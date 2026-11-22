import { useMemo } from 'react'
import RecordsPage from '../components/common/RecordsPage'
import { feedbackApi, followupApi } from '../api/recordsApi'
import { sourcingPlatformApi } from '../api/reportingApi'
import { useAuth } from '../hooks/useAuth'
import { useFetch } from '../hooks/useFetch'
import { formatDate } from '../utils/format'
import StatusBadge from '../components/common/StatusBadge'
import { clean, clamp } from './recordHelpers'


const STATUS_OPTIONS = [
  { value: 'SOURCED', label: 'Sourced' },
  { value: 'SCREENED', label: 'Screened' },
  { value: 'SUBMITTED', label: 'Submitted to client' },
  { value: 'INTERVIEW_SCHEDULED', label: 'Interview scheduled' },
  { value: 'INTERVIEW_ATTENDED', label: 'Interview attended' },
  { value: 'OFFERED', label: 'Offered' },
  { value: 'SELECTED', label: 'Selected (legacy)' },
  { value: 'JOINED', label: 'Joined' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'WITHDRAWN', label: 'Withdrawn' },
  { value: 'DROPPED', label: 'Dropped (legacy)' },
]

  // The five platforms the business uses, in the same order everywhere.
  const FALLBACK_PLATFORMS = ['Naukri', 'Apna', 'LinkedIn', 'Careers', 'Others']

const YES_NO = [
  { value: 'true', label: 'Yes' },
  { value: 'false', label: 'No' },
]

const asBool = (v) => (v === '' || v == null ? null : v === 'true')

/** The pipeline stage drives which later fields make sense to fill in. */
const isScheduled = (r) => r.interviewScheduled === true || r.interviewAttended === true
const isJoined = (r) => r.status === 'JOINED'
const isSelected = (r) => r.status === 'JOINED' || r.status === 'SELECTED'

const feedbackConfig = {
  title: 'Candidate tracking',
  subtitle: 'Candidate records from sourcing through interview, offer and joining.',
  noun: 'Candidate',
  idKey: 'feedbackId',
  api: feedbackApi,
  exportEndpoint: '/api/admin/feedback/export',
  exportFilename: 'candidates',
  searchKeys: ['candidateName', 'candidateEmail', 'currentCompany', 'currentDesignation', 'clientName', 'hrName', 'status', 'sourcingPlatform'],
  fields: [
    // candidate details
    { name: 'candidateName', label: 'Candidate name', required: true, maxLength: 100, half: true },
    { name: 'candidateEmail', label: 'Candidate email', type: 'email', maxLength: 150, half: true },
    { name: 'contactNumber', label: 'Contact number', type: 'tel', maxLength: 30, half: true },
    { name: 'hrName', label: 'HR name', maxLength: 100, half: true },
    { name: 'currentLocation', label: 'Current location', maxLength: 100, half: true },
    { name: 'preferredLocation', label: 'Preferred location', maxLength: 100, half: true },
    { name: 'currentCompany', label: 'Current company', maxLength: 150, half: true },
    { name: 'currentDesignation', label: 'Current designation', maxLength: 100, half: true },
    { name: 'currentCtc', label: 'Current CTC', type: 'number', min: 0, step: '0.01', half: true },
    { name: 'expectedCtc', label: 'Expected CTC', type: 'number', min: 0, step: '0.01', half: true },
    { name: 'noticePeriodDays', label: 'Notice period (days)', type: 'number', min: 0, max: 365, half: true },
    { name: 'clientName', label: 'Client name', maxLength: 150, half: true },
    { name: 'sourcingPlatform', label: 'Sourcing platform', type: 'select', half: true, placeholder: 'Unknown' },
    { name: 'sourcedDate', label: 'Sourced date', type: 'date', half: true, hint: 'Defaults to today when left blank' },

    // interview
    { name: 'interviewScheduled', label: 'Interview scheduled?', type: 'select', options: YES_NO, half: true, placeholder: 'Not set' },
    { name: 'interviewScheduledDate', label: 'Interview date', type: 'date', half: true },
    { name: 'interviewAttended', label: 'Candidate attended?', type: 'select', options: YES_NO, half: true, placeholder: 'Not set' },

    // outcome
    { name: 'status', label: 'Status', type: 'select', options: STATUS_OPTIONS, half: true, placeholder: 'Sourced' },
    { name: 'googleFeedbackGiven', label: 'Google feedback given?', type: 'select', options: YES_NO, half: true, placeholder: 'Not set', hint: 'For joined candidates' },
    { name: 'comments', label: 'Notes', type: 'textarea', maxLength: 5000 },
  ],
  columns: [
    { key: 'candidateName', label: 'Candidate', sortable: true },
    { key: 'currentCompany', label: 'Company', sortable: true, render: (r) => r.currentCompany || '—' },
    { key: 'designation', label: 'Designation', render: (r) => r.currentDesignation || '—' },
    { key: 'clientName', label: 'Client', sortable: true, render: (r) => r.clientName || '—' },
    { key: 'status', label: 'Status', sortable: true, render: (r) => <StatusBadge value={r.status} /> },
    { key: 'interview', label: 'Interview', render: (r) => (isScheduled(r) ? formatDate(r.interviewScheduledDate) || 'Scheduled' : '—') },
    { key: 'feedback', label: 'Google feedback', render: (r) => (isJoined(r) ? (r.googleFeedbackGiven ? 'Yes' : 'No') : '—') },
  ],
  rowDetail: (r) => [
    ['Email', r.candidateEmail],
    ['Contact', r.contactNumber],
    ['HR name', r.hrName],
    ['Current location', r.currentLocation],
    ['Preferred location', r.preferredLocation],
    ['Current CTC', r.currentCtc],
    ['Expected CTC', r.expectedCtc],
    ['Notice period', r.noticePeriodDays == null ? null : `${r.noticePeriodDays} days`],
    ['Designation', r.currentDesignation],
    ['Interview scheduled', isScheduled(r) ? (r.interviewAttended ? 'Attended' : 'Scheduled') : 'No'],
    ['Interview date', r.interviewScheduledDate && formatDate(r.interviewScheduledDate)],
    ['Selected', isSelected(r) ? 'Yes' : 'No'],
    ['Joined', isJoined(r) ? 'Yes' : 'No'],
    ['Sourcing platform', r.sourcingPlatform || 'Unknown'],
    ['Sourced date', r.sourcedDate && formatDate(r.sourcedDate)],
    ['Google feedback', isJoined(r) ? (r.googleFeedbackGiven ? 'Given' : 'Not yet') : null],
    ['Notes', r.comments],
  ],
  toPayload: (v) => ({
    candidateName: v.candidateName.trim(),
    candidateEmail: clean(v.candidateEmail),
    contactNumber: clean(v.contactNumber),
    hrName: clean(v.hrName),
    currentLocation: clean(v.currentLocation),
    preferredLocation: clean(v.preferredLocation),
    currentCompany: clean(v.currentCompany),
    currentDesignation: clean(v.currentDesignation),
    currentCtc: clean(v.currentCtc) == null ? null : Number(v.currentCtc),
    expectedCtc: clean(v.expectedCtc) == null ? null : Number(v.expectedCtc),
    noticePeriodDays: clean(v.noticePeriodDays) == null ? null : Number(v.noticePeriodDays),
    clientName: clean(v.clientName),
    sourcingPlatform: clean(v.sourcingPlatform),
    sourcedDate: clean(v.sourcedDate),
    interviewScheduled: asBool(v.interviewScheduled),
    interviewScheduledDate: clean(v.interviewScheduledDate),
    interviewAttended: asBool(v.interviewAttended),
    status: clean(v.status) ?? 'SOURCED',
    googleFeedbackGiven: asBool(v.googleFeedbackGiven),
    comments: clean(v.comments),
  }),
}

const followupConfig = {
  title: 'Follow-ups',
  subtitle: 'Track candidate joining status.',
  noun: 'Follow-up',
  idKey: 'followupId',
  api: followupApi,
  searchKeys: ['candidateName', 'client', 'joiningStatus', 'reason'],
  fields: [
    { name: 'candidateName', label: 'Candidate name', required: true, maxLength: 100, half: true },
    { name: 'client', label: 'Client', maxLength: 150, half: true },
    { name: 'joinDate', label: 'Join date', type: 'date', half: true },
    { name: 'joiningStatus', label: 'Joining status', maxLength: 30, half: true, hint: 'e.g. Joined / Pending / Dropped' },
    { name: 'reason', label: 'Reason / notes', type: 'textarea' },
  ],
  columns: [
    { key: 'candidateName', label: 'Candidate', sortable: true },
    { key: 'client', label: 'Client', sortable: true },
    { key: 'joinDate', label: 'Join date', sortable: true, render: (r) => formatDate(r.joinDate) },
    { key: 'joiningStatus', label: 'Status', sortable: true, render: (r) => r.joiningStatus || '—' },
    { key: 'reason', label: 'Reason', render: (r) => clamp(r.reason) },
  ],
  toPayload: (v) => ({ candidateName: v.candidateName.trim(), client: clean(v.client), joinDate: clean(v.joinDate), joiningStatus: clean(v.joiningStatus), reason: clean(v.reason) }),
}


/**
 * The platform select is filled from the company's own options. Renaming or
 * disabling an option never rewrites existing candidate records, so old values
 * keep displaying as text.
 */
export const FeedbackPage = () => {
  const { isAdmin } = useAuth()
  const fetcher = useMemo(
    () => () => (isAdmin ? sourcingPlatformApi.admin() : sourcingPlatformApi.employee()),
    [isAdmin],
  )
  const { data } = useFetch(fetcher)

  const config = useMemo(() => {
    const names = (data ?? []).filter((p) => p.active).map((p) => p.name)
    const platformOptions = (names.length ? names : FALLBACK_PLATFORMS).map((name) => ({
      value: name,
      label: name,
    }))
    return {
      ...feedbackConfig,
      fields: feedbackConfig.fields.map((field) =>
        field.name === 'sourcingPlatform' ? { ...field, options: platformOptions } : field,
      ),
    }
  }, [data])

  return <RecordsPage config={config} />
}
export const FollowupsPage = () => <RecordsPage config={followupConfig} />

