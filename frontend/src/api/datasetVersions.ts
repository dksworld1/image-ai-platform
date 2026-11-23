import type { ImageDatasetTarget } from './images'

export type DatasetVersion = {
  versionId: string; datasetId: string; versionNumber: number; name: string; description: string | null; changeNote: string | null
  imageCount: number; fileCount: number; createdAt: string; updatedAt: string; createdBy: string; updatedBy: string
  creatorUserId: string | null; creatorUsername: string | null
}
export type DatasetVersionEntry = {
  kind: 'images' | 'files'; entryId: string; originalFilename: string; description: string | null
  mediaType: string; sizeBytes: number; versionCount: number; sourceCreatedAt: string; sourceCreatedBy: string
}
async function request(path: string, token: string, signal?: AbortSignal, changeNote?: string): Promise<Response> {
  const creating = changeNote !== undefined
  let response: Response
  try {
    response = await fetch(path, {
      method: creating ? 'POST' : 'GET', headers: { Authorization: `Bearer ${token}`, ...(creating ? { 'Content-Type': 'application/json' } : {}) },
      body: creating ? JSON.stringify({ changeNote }) : undefined, signal,
    })
  } catch (reason) {
    if (signal?.aborted) throw reason
    throw new Error(creating ? '생성 결과를 확인할 수 없습니다. 새 버전을 다시 만들기 전에 목록을 새로고침해 확인해 주세요.' : '서버에 연결할 수 없습니다. 다시 시도해 주세요.')
  }
  if (response.status === 400) throw new Error('변경 설명은 2,000자 이내로 입력해 주세요.')
  if (response.status === 401) throw new Error('로그인이 만료되었습니다. 다시 로그인해 주세요.')
  if (response.status === 403) throw new Error(creating ? '버전 생성 권한이 없거나 이 데이터셋의 관리자가 아닙니다. 활성 워크스페이스 멤버십도 확인해 주세요.' : '버전 조회 권한이 없거나 활성 워크스페이스 멤버가 아닙니다.')
  if (response.status === 404) throw new Error('이 버전 또는 버전에 포함된 파일을 찾을 수 없습니다.')
  if (!response.ok) throw new Error(creating ? '생성 결과를 확인할 수 없습니다. 목록을 새로고침해 확인해 주세요.' : '버전 정보를 불러오지 못했습니다. 다시 시도해 주세요.')
  return response
}
const base = (datasetId: string) => `/api/datasets/${encodeURIComponent(datasetId)}/versions`
export async function fetchVersionTargets(token: string, signal?: AbortSignal): Promise<ImageDatasetTarget[]> {
  return (await request('/api/datasets/version-targets', token, signal)).json()
}
export async function fetchDatasetVersions(datasetId: string, token: string, signal?: AbortSignal): Promise<DatasetVersion[]> {
  return (await request(base(datasetId), token, signal)).json()
}
export async function createDatasetVersion(datasetId: string, changeNote: string, token: string, signal?: AbortSignal): Promise<DatasetVersion> {
  return (await request(base(datasetId), token, signal, changeNote)).json()
}
export async function fetchVersionEntries(datasetId: string, versionId: string, token: string, signal?: AbortSignal): Promise<DatasetVersionEntry[]> {
  return (await request(`${base(datasetId)}/${encodeURIComponent(versionId)}/entries`, token, signal)).json()
}
export async function fetchVersionContent(datasetId: string, versionId: string, entry: DatasetVersionEntry, token: string, signal?: AbortSignal): Promise<Blob> {
  return (await request(`${base(datasetId)}/${encodeURIComponent(versionId)}/${entry.kind}/${encodeURIComponent(entry.entryId)}/content`, token, signal)).blob()
}

export async function restoreDatasetVersion(datasetId: string, versionId: string, token: string, signal?: AbortSignal): Promise<DatasetVersion> {
  let response: Response
  try {
    response = await fetch(`${base(datasetId)}/${encodeURIComponent(versionId)}/restore`, {
      method: 'POST', headers: { Authorization: `Bearer ${token}` }, signal,
    })
  } catch (reason) {
    if (signal?.aborted) throw reason
    throw new Error('적용 결과를 확인할 수 없습니다. 현재 파일 목록을 확인한 뒤 다시 시도해 주세요.')
  }
  if (response.status === 401) throw new Error('로그인이 만료되었습니다. 다시 로그인해 주세요.')
  if (response.status === 403) throw new Error('버전 편집 시작·조회 권한이 없거나 이 데이터셋을 관리할 수 없습니다.')
  if (response.status === 404) throw new Error('기준으로 사용할 버전을 찾을 수 없습니다.')
  if (response.status === 409) throw new Error('기준 버전의 파일이 없거나 크기가 달라 적용하지 못했습니다. 현재 편집본은 유지됩니다.')
  if (!response.ok) throw new Error('적용 결과를 확인할 수 없습니다. 현재 파일 목록을 확인한 뒤 다시 시도해 주세요.')
  return response.json() as Promise<DatasetVersion>
}
