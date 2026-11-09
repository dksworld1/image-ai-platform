import type { ReactNode } from 'react'

export default function AppLayout({ children, username, onLogout }: {
  children: ReactNode
  username: string
  onLogout: () => void
}) {
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
      <main className="app-main">{children}</main>
    </div>
  )
}
