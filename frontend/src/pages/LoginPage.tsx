import { useRef, useState, type SubmitEvent } from 'react'
import { login, type AuthToken } from '../api/auth'

export default function LoginPage({ onLogin }: { onLogin: (token: AuthToken) => void }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const submitting = useRef(false)

  async function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting.current) return
    submitting.current = true
    setPending(true)
    setError(null)
    try {
      const token = await login(username.trim(), password)
      setPassword('')
      onLogin(token)
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : '로그인에 실패했습니다.')
    } finally {
      submitting.current = false
      setPending(false)
    }
  }

  return (
    <main className="login-shell">
      <section className="card login-card" aria-labelledby="login-title">
        <p className="eyebrow">IMAGE AI PLATFORM</p>
        <h1 id="login-title">로그인</h1>
        <p className="intro">계정으로 로그인해 작업을 시작하세요.</p>
        <form className="login-form" onSubmit={handleSubmit} aria-busy={pending}>
          <label htmlFor="username">아이디</label>
          <input id="username" name="username" autoComplete="username" required
            value={username} onChange={event => setUsername(event.target.value)} disabled={pending} />
          <label htmlFor="password">비밀번호</label>
          <input id="password" name="password" type="password" autoComplete="current-password" required
            value={password} onChange={event => setPassword(event.target.value)} disabled={pending} />
          {error && <p className="login-error" role="alert">{error}</p>}
          <button type="submit" disabled={pending || !username.trim() || !password}>
            {pending ? '로그인 중…' : '로그인'}
          </button>
          <span className="login-progress" role="status">{pending ? '로그인을 확인하고 있습니다.' : ''}</span>
        </form>
      </section>
    </main>
  )
}
