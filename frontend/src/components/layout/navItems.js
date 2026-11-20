/**
 * Navigation per role, grouped so related work sits together.
 * Hiding a link is UX only: the backend enforces every permission.
 */
const overview = [{ to: '/dashboard', label: 'Dashboard', icon: 'dashboard' }]

export const ADMIN_NAV = [
  { section: 'Overview', items: overview },
  {
    section: 'People',
    items: [
      { to: '/employees', label: 'Employees', icon: 'users' },
      { to: '/administrators', label: 'Administrators', icon: 'shield' },
      { to: '/clients', label: 'Clients', icon: 'briefcase' },
    ],
  },
  {
    section: 'Time & leave',
    items: [
      { to: '/attendance', label: 'Attendance', icon: 'clock' },
      { to: '/leaves', label: 'Leave requests', icon: 'calendar', badgeKey: 'pendingLeaves' },
      { to: '/wfh', label: 'WFH requests', icon: 'home', badgeKey: 'pendingWfh' },
      { to: '/office-settings', label: 'Office settings', icon: 'pin' },
    ],
  },
  {
    section: 'Reports',
    items: [
      { to: '/reports', label: 'Daily reports', icon: 'file' },
      { to: '/weekly-reports', label: 'Weekly reports', icon: 'chart' },
    ],
  },
  {
    section: 'Development',
    items: [
      { to: '/performance-tracker', label: 'Performance', icon: 'chart' },
      { to: '/candidate-tracking', label: 'Candidate tracking', icon: 'message' },
    ],
  },
]

export const EMPLOYEE_NAV = [
  { section: 'Overview', items: overview },
  {
    section: 'My work',
    items: [
      { to: '/attendance', label: 'Attendance', icon: 'clock' },
      { to: '/leaves', label: 'My leave', icon: 'calendar', badgeKey: 'pendingLeaves' },
      { to: '/wfh', label: 'Work from home', icon: 'home', badgeKey: 'pendingWfh' },
      { to: '/reports', label: 'Daily reports', icon: 'file' },
      { to: '/weekly-reports', label: 'Weekly reports', icon: 'chart' },
    ],
  },
  {
    section: 'Development',
    items: [
      { to: '/performance', label: 'Performance', icon: 'chart' },
      { to: '/candidate-tracking', label: 'Candidate tracking', icon: 'message' },
      { to: '/followups', label: 'Follow-ups', icon: 'phone' },
    ],
  },
  { section: 'Account', items: [{ to: '/profile', label: 'My profile', icon: 'user' }] },
]

/** Flat list, handy for breadcrumbs and route titles. */
export const navFor = (isAdmin) => (isAdmin ? ADMIN_NAV : EMPLOYEE_NAV).flatMap((g) => g.items)
