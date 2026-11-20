import { useEffect, useState } from 'react'
import { adminLeaveApi, employeeLeaveApi } from '../api/leaveApi'
import { adminWfhApi, employeeWfhApi } from '../api/wfhApi'

/**
 * Sidebar badge counts. Uses the existing list endpoints so it works regardless of
 * newer summary endpoints; a failure simply shows no badge instead of a fake number.
 */
export function useNavBadges(isAdmin) {
  const [badges, setBadges] = useState({ pendingLeaves: 0, pendingWfh: 0 })

  useEffect(() => {
    let active = true
    const leaves = isAdmin ? adminLeaveApi.list : employeeLeaveApi.list
    const wfh = isAdmin ? adminWfhApi.list : employeeWfhApi.list

    Promise.all([leaves(), wfh()])
      .then(([leaveRows, wfhRows]) => {
        if (!active) return
        setBadges({
          pendingLeaves: Array.isArray(leaveRows)
            ? leaveRows.filter((l) => l.status === 'PENDING').length
            : 0,
          pendingWfh: Array.isArray(wfhRows)
            ? wfhRows.filter((w) => w.status === 'PENDING').length
            : 0,
        })
      })
      .catch(() => active && setBadges({ pendingLeaves: 0, pendingWfh: 0 }))

    return () => {
      active = false
    }
  }, [isAdmin])

  return badges
}
