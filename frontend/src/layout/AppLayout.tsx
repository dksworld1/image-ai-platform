import type { ReactNode } from 'react'

export default function AppLayout({ children, userId, username, onLogout, currentPath }: {
  children: ReactNode
  userId: string
  username: string | null
  onLogout: () => void
  currentPath: string
}) {
  return (
    <div className={`app-layout${currentPath === '/datasets' || currentPath === '/datasets/images' ? ' dataset-list-layout' : ''}`}>
      <header className="app-header">
        <a className="brand" href="/">Image AI Platform</a>
        <div className="header-account">
          <div className="account-info">
            <span className="account-label">로그인 계정</span>
            <span className="account-user-id">{userId}</span>
            {username && <span className="account-name">{username}</span>}
          </div>
          <button className="logout-button" type="button" onClick={onLogout}>로그아웃</button>
        </div>
      </header>
      <nav className="app-navigation" aria-label="업무 메뉴">
        <a className="navigation-link" href="/" aria-current={currentPath === '/' ? 'page' : undefined}>시작 화면</a>
        <details className="navigation-group" open={currentPath === '/datasets' || currentPath.startsWith('/datasets/')}>
          <summary><h2 id="dataset-menu-title">데이터셋</h2></summary>
          <ul aria-labelledby="dataset-menu-title">
            <li><a className="navigation-link" href="/datasets"
              aria-current={currentPath === '/datasets' ? 'page' : undefined}>데이터셋 목록</a></li>
            <li>
              <a className="navigation-link" href="/datasets/new"
                aria-current={currentPath === '/datasets/new' ? 'page' : undefined}>데이터셋 생성</a>
            </li>
            <li><a className="navigation-link" href="/datasets/images"
              aria-current={currentPath === '/datasets/images' ? 'page' : undefined}>이미지 관리</a></li>
          </ul>
        </details>
      </nav>
      <main className="app-main">{children}</main>
    </div>
  )
}
