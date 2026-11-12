
export type CreateDatasetRequest = { name: string; description: string | null; workspaceIds: string[] }

export type Dataset = {
  datasetId: string
  name: string
  description: string | null
  createdAt: string
  updatedAt: string
  createdBy: string
  updatedBy: string
  workspaceId: string
}

export type DatasetListItem = Pick<Dataset, 'datasetId' | 'name' | 'description' | 'createdAt' | 'updatedAt'> & {
  workspaceId: string
  workspaceName: string
  creatorUserId: string | null
  creatorUsername: string | null
}

export async function fetchDatasets(accessToken: string, signal?: AbortSignal): Promise<DatasetListItem[]> {
  let response: Response
  try {
    response = await fetch('/api/datasets', { headers: { Authorization: `Bearer ${accessToken}` }, signal })
  } catch (reason) {
    if (signal?.aborted) throw reason
    throw new Error('서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.')
  }
  if (response.status === 401) throw new Error('로그인이 만료되었거나 계정을 사용할 수 없습니다. 다시 로그인해 주세요.')
  if (response.status === 403) throw new Error('데이터셋을 조회할 권한이 없습니다.')
  if (!response.ok) throw new Error('데이터셋 목록을 불러오지 못했습니다. 다시 시도해 주세요.')
  return response.json() as Promise<DatasetListItem[]>
}

export async function fetchDatasetDetail(datasetId: string, accessToken: string, signal?: AbortSignal): Promise<DatasetListItem> {
  let response: Response
  try {
    response = await fetch(`/api/datasets/${encodeURIComponent(datasetId)}`, {
      headers: { Authorization: `Bearer ${accessToken}` }, signal,
    })
  } catch (reason) {
    if (signal?.aborted) throw reason
    throw new Error('서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.')
  }
  if (response.status === 401) throw new Error('로그인이 만료되었거나 계정을 사용할 수 없습니다. 다시 로그인해 주세요.')
  if (response.status === 403) throw new Error('상세 조회 권한이 없거나 활성 워크스페이스 멤버가 아니거나 조회할 수 없는 데이터셋입니다.')
  if (!response.ok) throw new Error('데이터셋 상세를 불러오지 못했습니다. 다시 시도해 주세요.')
  return response.json() as Promise<DatasetListItem>
}

export async function deleteDataset(datasetId: string, accessToken: string): Promise<void> {
  let response: Response
  try {
    response = await fetch(`/api/datasets/${encodeURIComponent(datasetId)}`, {
      method: 'DELETE', headers: { Authorization: `Bearer ${accessToken}` },
    })
  } catch {
    throw new Error('삭제 결과를 확인할 수 없습니다. 목록을 새로고침해 확인해 주세요.')
  }
  if (response.status === 401) throw new Error('로그인이 만료되었거나 계정을 사용할 수 없습니다. 다시 로그인해 주세요.')
  if (response.status === 403) throw new Error('삭제 권한이 없거나 현재 삭제할 수 없는 데이터셋입니다.')
  if (!response.ok) throw new Error('데이터셋을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.')
}

export async function createDataset(request: CreateDatasetRequest, accessToken: string): Promise<Dataset[]> {
  let response: Response
  try {
    response = await fetch('/api/datasets', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` },
      body: JSON.stringify(request),
    })
  } catch {
    throw new Error('서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.')
  }
  if (response.status === 400) throw new Error('워크스페이스를 선택하고 이름 1~200자, 설명 2,000자 이내로 입력해 주세요.')
  if (response.status === 401) throw new Error('로그인이 만료되었거나 계정을 사용할 수 없습니다. 다시 로그인해 주세요.')
  if (response.status === 403) throw new Error('생성 권한이 없거나 선택한 워크스페이스에 접근할 수 없습니다. 워크스페이스를 다시 불러와 확인해 주세요.')
  if (!response.ok) throw new Error('데이터셋을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.')
  return response.json() as Promise<Dataset[]>
}
