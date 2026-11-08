import { useEffect, useState } from 'react'
import { fetchHealth, type HealthStatus } from '../api/health'

export default function HomePage() {
  const [health, setHealth] = useState<HealthStatus | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchHealth().then(setHealth).catch((reason: unknown) => {
      setError(reason instanceof Error ? reason.message : 'API 연결에 실패했습니다.')
    })
  }, [])

  return (
    <>
      <section className="card" aria-labelledby="title">
        <p className="eyebrow">IMAGE AI PLATFORM</p>
        <h1 id="title">프로젝트 시작 화면</h1>
        <p className="intro">API 서버와 연결할 수 있는지 확인합니다.</p>
        <div className="status" role="status">
          <span className={`dot ${health?.status === 'UP' ? 'online' : ''}`} />
          {health ? `백엔드 응답 · ${health.status}` : error ?? '백엔드 상태 확인 중…'}
        </div>
      </section>
    </>
  )
}
