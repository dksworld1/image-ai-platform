export type AuthToken = {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  userKey: string
  userId: string
  username: string | null
}

export async function login(userId: string, password: string): Promise<AuthToken> {
  let response: Response
  try {
    response = await fetch('/api/auth/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId, password }),
    })
  } catch {
    throw new Error('서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.')
  }
  if (response.status === 401) {
    throw new Error('아이디 또는 비밀번호를 확인해 주세요.')
  }
  if (!response.ok) {
    throw new Error('로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.')
  }
  return response.json() as Promise<AuthToken>
}
