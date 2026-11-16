import { useEffect, useState } from 'react'
import { adminLeaveApi, employeeLeaveApi } from '../api/leaveApi'

/**
 * Sidebar badge counts. Uses the existing list endpoints so it works regardless of
 * newer summary endpoints; a failure simply shows no badge instead of a fake number.
 */
export function useNavBadges(isAdmin) {
  const [badges, setBadges] = useState({ pendingLeaves: 0 })

  useEffect(() => {
    let active = true
    const load = isAdmin ? adminLeaveApi.list : employeeLeaveApi.list
    load()
      .then((leaves) => {
        if (!active || !Array.isArray(leaves)) return
        setBadges({ pendingLeaves: leaves.filter((l) => l.status === 'PENDING').length })
      })
      .catch(() => active && setBadges({ pendingLeaves: 0 }))
    return () => {
      active = false
    }
  }, [isAdmin])

  return badges
}
