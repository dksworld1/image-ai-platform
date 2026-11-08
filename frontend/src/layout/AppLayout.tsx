import type { ReactNode } from 'react'

export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <div className="app-layout">
      <header className="app-header">
        <a className="brand" href="/">Image AI Platform</a>
      </header>
      <nav className="app-nav" aria-label="주요 메뉴">
        <a className="nav-link" href="/" aria-current="page">시작</a>
      </nav>
      <main className="app-main">{children}</main>
    </div>
  )
}
