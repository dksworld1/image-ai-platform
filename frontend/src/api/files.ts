export type DatasetFile = {
  fileId: string
  datasetId: string
  originalFilename: string
  description: string | null
  mediaType: string
  sizeBytes: number
  createdAt: string
  firstRegisteredAt: string | null
  createdBy: string
  updatedAt: string
  updatedBy: string
  versionCount: number
  creatorUserId: string | null
  creatorUsername: string | null
  updaterUserId: string | null
  updaterUsername: string | null
}

export type FileDatasetTarget = { datasetId: string; name: string; workspaceId: string; workspaceName: string }

export async function fetchFileDatasetTargets(accessToken: string, signal?: AbortSignal): Promise<FileDatasetTarget[]> {
  const response = await fetch('/api/datasets/file-targets', { headers: { Authorization: `Bearer ${accessToken}` }, signal })
  if (response.status === 401) throw new Error('로그인이 만료되었습니다. 다시 로그인해 주세요.')
  if (response.status === 403) throw new Error('데이터 파일을 조회할 권한이 없습니다.')
  if (!response.ok) throw new Error('데이터 파일 관리 대상 목록을 불러오지 못했습니다.')
  return response.json() as Promise<FileDatasetTarget[]>
}

export class FileUploadError extends Error {
  constructor(message: string, public readonly status: number | null) {
    super(message)
  }
}

export async function uploadFile(datasetId: string, file: File, accessToken: string, signal?: AbortSignal, replaceExisting = false, description = ''): Promise<DatasetFile> {
  const body = new FormData()
  body.append('file', file)
  if (file.webkitRelativePath) body.append('relativePath', file.webkitRelativePath)
  body.append('description', description)
  let response: Response
  try {
    response = await fetch(`/api/datasets/${encodeURIComponent(datasetId)}/files${replaceExisting ? '?replaceExisting=true' : ''}`, {
      method: 'POST', headers: { Authorization: `Bearer ${accessToken}` }, body, signal,
    })
  } catch (reason) {
    if (signal?.aborted) throw reason
    throw new FileUploadError('등록 결과를 확인할 수 없습니다. 목록을 확인한 뒤 다시 시도해 주세요.', null)
  }
  if (response.status === 400) throw new FileUploadError('파일명·용량·설명 2,000자 제한을 확인해 주세요. 빈 파일은 업로드할 수 없습니다.', 400)
  if (response.status === 401) throw new FileUploadError('로그인이 만료되었습니다. 다시 로그인해 주세요.', 401)
  if (response.status === 403) throw new FileUploadError('업로드 권한이 없거나 접근할 수 없는 데이터셋입니다.', 403)
  if (response.status === 409) throw new FileUploadError('이미 존재하는 데이터 파일입니다.', 409)
  if (response.status === 413) throw new FileUploadError('서버의 파일 또는 요청 용량 제한을 초과했습니다.', 413)
  if (!response.ok) throw new FileUploadError('등록 결과를 확인할 수 없습니다. 목록을 확인한 뒤 다시 시도해 주세요.', response.status)
  return response.json() as Promise<DatasetFile>
}

export type FileDescriptionChange = { fileId: string; description: string | null }

export async function saveFileDescriptions(datasetId: string, items: FileDescriptionChange[], accessToken: string, signal?: AbortSignal): Promise<DatasetFile[]> {
  let response: Response
  try {
    response = await fetch(`/api/datasets/${encodeURIComponent(datasetId)}/files/descriptions`, {
      method: 'PATCH', headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ items }), signal,
    })
  } catch (reason) {
    if (signal?.aborted) throw reason
    throw new Error('저장 결과를 확인할 수 없습니다. 목록을 새로고침해 확인해 주세요.')
  }
  if (response.status === 400) throw new Error('설명은 데이터 파일별 2,000자 이내로 입력해 주세요.')
  if (response.status === 401) throw new Error('로그인이 만료되었습니다. 다시 로그인해 주세요.')
  if (response.status === 403) throw new Error('데이터 파일 수정 권한이 없거나 현재 수정할 수 없는 데이터셋입니다.')
  if (response.status === 404) throw new Error('삭제되거나 교체된 데이터 파일이 있어 전체 저장이 취소되었습니다. 목록을 새로고침해 주세요.')
  if (!response.ok) throw new Error('설명을 저장하지 못했습니다. 목록을 확인한 뒤 다시 시도해 주세요.')
  return response.json() as Promise<DatasetFile[]>
}

export async function fetchFiles(datasetId: string, accessToken: string, signal?: AbortSignal): Promise<DatasetFile[]> {
  const response = await fetch(`/api/datasets/${encodeURIComponent(datasetId)}/files`, {
    headers: { Authorization: `Bearer ${accessToken}` }, signal,
  })
  if (response.status === 401) throw new Error('로그인이 만료되었습니다. 다시 로그인해 주세요.')
  if (response.status === 403) throw new Error('데이터 파일 조회 권한이 없거나 접근할 수 없는 데이터셋입니다.')
  if (!response.ok) throw new Error('데이터 파일 목록을 불러오지 못했습니다. 다시 시도해 주세요.')
  return response.json() as Promise<DatasetFile[]>
}

export async function deleteFile(datasetId: string, fileId: string, accessToken: string, signal?: AbortSignal): Promise<void> {
  let response: Response
  try {
    response = await fetch(`/api/datasets/${encodeURIComponent(datasetId)}/files/${encodeURIComponent(fileId)}`, {
      method: 'DELETE', headers: { Authorization: `Bearer ${accessToken}` }, signal,
    })
  } catch (reason) {
    if (signal?.aborted) throw reason
    throw new Error('삭제 결과를 확인할 수 없습니다. 목록을 새로고침해 확인해 주세요.')
  }
  if (response.status === 401) throw new Error('로그인이 만료되었습니다. 다시 로그인해 주세요.')
  if (response.status === 403) throw new Error('데이터 파일 삭제 권한이 없거나 현재 삭제할 수 없는 데이터셋입니다.')
  if (response.status === 404) throw new Error('이미 삭제되었거나 교체된 데이터 파일입니다. 목록을 새로고침해 주세요.')
  if (!response.ok) throw new Error('데이터 파일을 삭제하지 못했습니다. 목록을 새로고침해 확인해 주세요.')
}

export async function fetchFileContent(datasetId: string, fileId: string, accessToken: string, signal: AbortSignal): Promise<Blob> {
  const response = await fetch(`/api/datasets/${encodeURIComponent(datasetId)}/files/${encodeURIComponent(fileId)}/content`, {
    headers: { Authorization: `Bearer ${accessToken}` }, signal,
  })
  if (response.status === 401) throw new Error('로그인이 만료되었습니다. 다시 로그인해 주세요.')
  if (response.status === 403) throw new Error('데이터 파일 조회 권한이 없거나 접근할 수 없는 데이터셋입니다.')
  if (response.status === 404) throw new Error('데이터 파일을 찾을 수 없습니다. 목록을 새로고침해 주세요.')
  if (!response.ok) throw new Error('원본 데이터 파일을 불러오지 못했습니다.')
  return response.blob()
}
