import { useCallback, useEffect, useState } from 'react'
import type { AuthToken } from './api/auth'
import { clearAuthSession, loadAuthSession, saveAuthSession } from './authSession'
import AppLayout from './layout/AppLayout'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import DatasetCreatePage from './pages/DatasetCreatePage'
import DatasetListPage from './pages/DatasetListPage'
import ImageListPage from './pages/ImageListPage'

export default function App() {
  const [token, setToken] = useState(loadAuthSession)

  const logout = useCallback(() => {
    clearAuthSession()
    window.history.replaceState(null, '', '/')
    setToken(null)
  }, [])

  useEffect(() => {
    if (token === null) return
    const timeout = window.setTimeout(logout, Math.max(0, token.expiresAt - Date.now()))
    return () => window.clearTimeout(timeout)
  }, [token, logout])

  function handleLogin(result: AuthToken) {
    setToken(saveAuthSession(result))
  }

  if (token === null) return <LoginPage onLogin={handleLogin} />

  const path = window.location?.pathname ?? '/'

  return (
    <AppLayout userId={token.userId} username={token.username} onLogout={logout} currentPath={path}>
      {path === '/' ? <HomePage /> : path === '/datasets' ? <DatasetListPage accessToken={token.accessToken} />
        : path === '/datasets/new' ? <DatasetCreatePage accessToken={token.accessToken} />
        : path === '/datasets/images' ? <ImageListPage accessToken={token.accessToken} /> : (
        <section>
          <h1>페이지를 찾을 수 없습니다</h1>
          <a href="/">시작 화면으로 이동</a>
        </section>
      )}
    </AppLayout>
  )
}
