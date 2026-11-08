import type { AuthToken } from './api/auth'

const storageKey = 'image-ai-platform.auth'

export type AuthSession = AuthToken & { expiresAt: number; username: string }

export function saveAuthSession(token: AuthToken, username: string): AuthSession {
  const session = { ...token, username, expiresAt: Date.now() + token.expiresIn * 1000 }
  window.sessionStorage.setItem(storageKey, JSON.stringify(session))
  return session
}

export function clearAuthSession() {
  window.sessionStorage.removeItem(storageKey)
}

export function loadAuthSession(): AuthSession | null {
  if (typeof window === 'undefined') return null
  const stored = window.sessionStorage.getItem(storageKey)
  if (stored === null) return null
  try {
    const session = JSON.parse(stored) as AuthSession
    if (session && typeof session.accessToken === 'string' && session.accessToken.length > 0
      && typeof session.username === 'string' && session.username.trim().length > 0
      && session.tokenType === 'Bearer' && Number.isFinite(session.expiresIn) && session.expiresIn > 0
      && Number.isFinite(session.expiresAt) && session.expiresAt > Date.now()) return session
  } catch {
    // 손상된 저장값은 로그인 상태로 복원하지 않습니다.
  }
  clearAuthSession()
  return null
}
