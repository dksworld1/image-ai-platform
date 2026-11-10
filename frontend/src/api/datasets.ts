export type CreateDatasetRequest = { name: string; description: string | null }

export type Dataset = {
  datasetId: string
  name: string
  description: string | null
  createdAt: string
  updatedAt: string
  createdBy: string
  updatedBy: string
}

export async function createDataset(request: CreateDatasetRequest, accessToken: string): Promise<Dataset> {
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
  if (response.status === 400) throw new Error('이름은 1~200자, 설명은 2,000자 이내로 입력해 주세요.')
  if (response.status === 401) throw new Error('로그인이 만료되었거나 계정을 사용할 수 없습니다. 다시 로그인해 주세요.')
  if (response.status === 403) throw new Error('데이터셋을 생성할 권한이 없습니다.')
  if (!response.ok) throw new Error('데이터셋을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.')
  return response.json() as Promise<Dataset>
}
