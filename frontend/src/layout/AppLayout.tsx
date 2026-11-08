import { useEffect, useState, type ReactNode } from 'react'
import { fetchNavigation, type UiNavigation } from '../api/ui'
import Navigation from './Navigation'

export default function AppLayout({ children, accessToken, username, onLogout }: {
  children: ReactNode
  accessToken: string
  username: string
  onLogout: () => void
}) {
  const [navigation, setNavigation] = useState<UiNavigation | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setNavigation(null)
    setError(null)
    fetchNavigation(accessToken, controller.signal).then(result => {
      if (!controller.signal.aborted) setNavigation(result)
    }).catch((reason: unknown) => {
      if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : '메뉴를 불러오지 못했습니다.')
    })
    return () => controller.abort()
  }, [accessToken, attempt])

  return (
    <div className="app-layout">
      <header className="app-header">
        <a className="brand" href="/">Image AI Platform</a>
        <div className="header-account">
          <div className="account-info">
            <span className="account-label">로그인 계정</span>
            <span className="account-username">{username}</span>
          </div>
          <button className="logout-button" type="button" onClick={onLogout}>로그아웃</button>
        </div>
      </header>
      <nav className="app-nav" aria-label="주요 메뉴">
        {error ? (
          <div className="nav-message">
            <p role="alert">{error}</p>
            <button type="button" onClick={() => setAttempt(value => value + 1)}>다시 불러오기</button>
          </div>
        ) : navigation ? <Navigation navigation={navigation} /> : <p className="nav-message" role="status">메뉴를 불러오는 중…</p>}
      </nav>
      <main className="app-main">{children}</main>
    </div>
  )
}
