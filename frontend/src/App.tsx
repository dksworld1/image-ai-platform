import { useState } from 'react'
import type { AuthToken } from './api/auth'
import AppLayout from './layout/AppLayout'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'

export default function App() {
  const [token, setToken] = useState<AuthToken | null>(null)

  if (token === null) return <LoginPage onLogin={setToken} />

  return (
    <AppLayout accessToken={token.accessToken}>
      <HomePage />
    </AppLayout>
  )
}
