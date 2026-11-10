import type { AuthToken } from './api/auth'

const storageKey = 'image-ai-platform.auth'

export type AuthSession = AuthToken & { expiresAt: number }

export function saveAuthSession(token: AuthToken): AuthSession {
  const session = { ...token, expiresAt: Date.now() + token.expiresIn * 1000 }
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
      && typeof session.userKey === 'string' && session.userKey.length > 0 && session.userKey !== '0'
      && typeof session.userId === 'string' && session.userId.trim().length > 0
      && (session.username === null || typeof session.username === 'string')
      && session.tokenType === 'Bearer' && Number.isFinite(session.expiresIn) && session.expiresIn > 0
      && Number.isFinite(session.expiresAt) && session.expiresAt > Date.now()) return session
  } catch {
    // 손상된 저장값은 로그인 상태로 복원하지 않습니다.
  }
  clearAuthSession()
  return null
}
