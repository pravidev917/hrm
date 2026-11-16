import { useCallback, useEffect, useMemo, useState } from 'react'
import { authApi } from '../api/authApi'
import { setUnauthorizedHandler } from '../api/axiosClient'
import { AuthContext } from './AuthContext'

// Only non-secret display data is cached (name/email/role). The JWT lives in an
// HttpOnly cookie that JavaScript cannot read.
const STORAGE_KEY = 'hrm.user'

function readCachedUser() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY))
  } catch {
    return null
  }
}
function cacheUser(user) {
  try {
    if (user) localStorage.setItem(STORAGE_KEY, JSON.stringify(user))
    else localStorage.removeItem(STORAGE_KEY)
  } catch {
    /* storage unavailable: the session still works, only the cached name is lost */
  }
}

const toUser = (r) => ({ userId: r.userId, name: r.name, email: r.email, role: r.role })

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [initializing, setInitializing] = useState(true)

  // Restore the session on page load by asking the backend who the cookie belongs to.
  useEffect(() => {
    let active = true
    authApi
      .me()
      .then((me) => {
        if (!active) return
        const role = me.authorities?.[0]?.authority?.replace('ROLE_', '')
        const cached = readCachedUser()
        const sameUser = cached && cached.email === me.email && cached.role === role
        const restored = { userId: sameUser ? cached.userId : null, name: sameUser ? cached.name : me.email, email: me.email, role }
        setUser(restored)
      })
      .catch(() => {
        if (active) {
          cacheUser(null)
          setUser(null)
        }
      })
      .finally(() => active && setInitializing(false))
    return () => {
      active = false
    }
  }, [])

  const clearSession = useCallback(() => {
    cacheUser(null)
    setUser(null)
  }, [])

  // Any 401 from a protected endpoint ends the session.
  useEffect(() => {
    setUnauthorizedHandler(clearSession)
    return () => setUnauthorizedHandler(null)
  }, [clearSession])

  const login = useCallback(async (role, credentials) => {
    const response =
      role === 'ADMIN'
        ? await authApi.loginAdmin(credentials)
        : await authApi.loginEmployee(credentials)
    const next = toUser(response)
    cacheUser(next)
    setUser(next)
    return next
  }, [])

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } catch {
      /* clear the local session even if the request fails */
    }
    clearSession()
  }, [clearSession])

  const value = useMemo(
    () => ({
      user,
      initializing,
      isAdmin: user?.role === 'ADMIN',
      isEmployee: user?.role === 'EMPLOYEE',
      login,
      logout,
    }),
    [user, initializing, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
