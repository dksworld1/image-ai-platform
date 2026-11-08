export type UiScreen = {
  screenCode: string
  displayName: string
  routePath: string
}

export type UiMenu = {
  menuCode: string
  parentMenuCode: string | null
  nodeType: 'GROUP' | 'SCREEN'
  displayName: string
  screenCode: string | null
  sortOrder: number
  enabled: boolean
}

export type UiNavigation = { screens: UiScreen[]; menus: UiMenu[] }

export async function fetchNavigation(accessToken: string, signal?: AbortSignal): Promise<UiNavigation> {
  let response: Response
  try {
    response = await fetch('/api/ui/navigation', {
      headers: { Authorization: `Bearer ${accessToken}` },
      signal,
    })
  } catch {
    throw new Error('서버에 연결할 수 없습니다. 메뉴를 다시 불러와 주세요.')
  }
  if (response.status === 401) throw new Error('로그인이 만료되었습니다. 새로고침 후 다시 로그인해 주세요.')
  if (!response.ok) throw new Error('메뉴를 불러오지 못했습니다. 다시 시도해 주세요.')
  return response.json() as Promise<UiNavigation>
}
