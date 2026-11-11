export type Workspace = { workspaceId: string; name: string }

export async function fetchWorkspaces(accessToken: string, signal?: AbortSignal): Promise<Workspace[]> {
  let response: Response
  try {
    response = await fetch('/api/workspaces', { headers: { Authorization: `Bearer ${accessToken}` }, signal })
  } catch (reason) {
    if (signal?.aborted) throw reason
    throw new Error('워크스페이스를 불러올 수 없습니다. 연결을 확인하고 다시 시도해 주세요.')
  }
  if (response.status === 401) throw new Error('로그인이 만료되었거나 계정을 사용할 수 없습니다. 다시 로그인해 주세요.')
  if (!response.ok) throw new Error('워크스페이스를 불러오지 못했습니다. 다시 시도해 주세요.')
  return response.json() as Promise<Workspace[]>
}
