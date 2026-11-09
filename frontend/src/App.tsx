import { useCallback, useEffect, useState } from 'react'
import type { AuthToken } from './api/auth'
import { clearAuthSession, loadAuthSession, saveAuthSession } from './authSession'
import AppLayout from './layout/AppLayout'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'

export default function App() {
  const [token, setToken] = useState(loadAuthSession)

  const logout = useCallback(() => {
    clearAuthSession()
    setToken(null)
  }, [])

  useEffect(() => {
    if (token === null) return
    const timeout = window.setTimeout(logout, Math.max(0, token.expiresAt - Date.now()))
    return () => window.clearTimeout(timeout)
  }, [token, logout])

  function handleLogin(result: AuthToken, username: string) {
    setToken(saveAuthSession(result, username))
  }

  if (token === null) return <LoginPage onLogin={handleLogin} />

  return (
    <AppLayout username={token.username} onLogout={logout}>
      <HomePage />
    </AppLayout>
  )
}
