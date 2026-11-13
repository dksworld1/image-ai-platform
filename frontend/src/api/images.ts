export type DatasetImage = {
  imageId: string
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
  thumbnailUrl: string | null
}

export type ImageDatasetTarget = { datasetId: string; name: string; workspaceId: string; workspaceName: string }

export async function fetchImageDatasetTargets(accessToken: string, signal?: AbortSignal): Promise<ImageDatasetTarget[]> {
  const response = await fetch('/api/datasets/image-targets', { headers: { Authorization: `Bearer ${accessToken}` }, signal })
  if (response.status === 401) throw new Error('로그인이 만료되었습니다. 다시 로그인해 주세요.')
  if (response.status === 403) throw new Error('이미지를 조회할 권한이 없습니다.')
  if (!response.ok) throw new Error('이미지 관리 대상 목록을 불러오지 못했습니다.')
  return response.json() as Promise<ImageDatasetTarget[]>
}

export class ImageUploadError extends Error {
  constructor(message: string, public readonly status: number | null) {
    super(message)
  }
}

export async function uploadImage(datasetId: string, file: File, accessToken: string, signal?: AbortSignal, replaceExisting = false, description = ''): Promise<DatasetImage> {
  const body = new FormData()
  body.append('file', file)
  if (file.webkitRelativePath) body.append('relativePath', file.webkitRelativePath)
  body.append('description', description)
  let response: Response
  try {
    response = await fetch(`/api/datasets/${encodeURIComponent(datasetId)}/images${replaceExisting ? '?replaceExisting=true' : ''}`, {
      method: 'POST', headers: { Authorization: `Bearer ${accessToken}` }, body, signal,
    })
  } catch (reason) {
    if (signal?.aborted) throw reason
    throw new ImageUploadError('등록 결과를 확인할 수 없습니다. 목록을 확인한 뒤 다시 시도해 주세요.', null)
  }
  if (response.status === 400) throw new ImageUploadError('파일명·설명 2,000자 제한·형식·이미지 크기를 확인해 주세요.', 400)
  if (response.status === 401) throw new ImageUploadError('로그인이 만료되었습니다. 다시 로그인해 주세요.', 401)
  if (response.status === 403) throw new ImageUploadError('업로드 권한이 없거나 접근할 수 없는 데이터셋입니다.', 403)
  if (response.status === 409) throw new ImageUploadError('이미 존재하는 이미지입니다.', 409)
  if (response.status === 413) throw new ImageUploadError('서버의 파일 또는 요청 용량 제한을 초과했습니다.', 413)
  if (!response.ok) throw new ImageUploadError('등록 결과를 확인할 수 없습니다. 목록을 확인한 뒤 다시 시도해 주세요.', response.status)
  return response.json() as Promise<DatasetImage>
}

export type ImageDescriptionChange = { imageId: string; description: string | null }

export async function saveImageDescriptions(datasetId: string, items: ImageDescriptionChange[], accessToken: string, signal?: AbortSignal): Promise<DatasetImage[]> {
  let response: Response
  try {
    response = await fetch(`/api/datasets/${encodeURIComponent(datasetId)}/images/descriptions`, {
      method: 'PATCH', headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ items }), signal,
    })
  } catch (reason) {
    if (signal?.aborted) throw reason
    throw new Error('저장 결과를 확인할 수 없습니다. 목록을 새로고침해 확인해 주세요.')
  }
  if (response.status === 400) throw new Error('설명은 이미지별 2,000자 이내로 입력해 주세요.')
  if (response.status === 401) throw new Error('로그인이 만료되었습니다. 다시 로그인해 주세요.')
  if (response.status === 403) throw new Error('이미지 수정 권한이 없거나 현재 수정할 수 없는 데이터셋입니다.')
  if (response.status === 404) throw new Error('삭제되거나 교체된 이미지가 있어 전체 저장이 취소되었습니다. 목록을 새로고침해 주세요.')
  if (!response.ok) throw new Error('설명을 저장하지 못했습니다. 목록을 확인한 뒤 다시 시도해 주세요.')
  return response.json() as Promise<DatasetImage[]>
}

export async function fetchImages(datasetId: string, accessToken: string, signal?: AbortSignal): Promise<DatasetImage[]> {
  const response = await fetch(`/api/datasets/${encodeURIComponent(datasetId)}/images`, {
    headers: { Authorization: `Bearer ${accessToken}` }, signal,
  })
  if (response.status === 401) throw new Error('로그인이 만료되었습니다. 다시 로그인해 주세요.')
  if (response.status === 403) throw new Error('이미지 조회 권한이 없거나 접근할 수 없는 데이터셋입니다.')
  if (!response.ok) throw new Error('이미지 목록을 불러오지 못했습니다. 다시 시도해 주세요.')
  return response.json() as Promise<DatasetImage[]>
}

export async function deleteImage(datasetId: string, imageId: string, accessToken: string, signal?: AbortSignal): Promise<void> {
  let response: Response
  try {
    response = await fetch(`/api/datasets/${encodeURIComponent(datasetId)}/images/${encodeURIComponent(imageId)}`, {
      method: 'DELETE', headers: { Authorization: `Bearer ${accessToken}` }, signal,
    })
  } catch (reason) {
    if (signal?.aborted) throw reason
    throw new Error('삭제 결과를 확인할 수 없습니다. 목록을 새로고침해 확인해 주세요.')
  }
  if (response.status === 401) throw new Error('로그인이 만료되었습니다. 다시 로그인해 주세요.')
  if (response.status === 403) throw new Error('이미지 삭제 권한이 없거나 현재 삭제할 수 없는 데이터셋입니다.')
  if (response.status === 404) throw new Error('이미 삭제되었거나 교체된 이미지입니다. 목록을 새로고침해 주세요.')
  if (!response.ok) throw new Error('이미지를 삭제하지 못했습니다. 목록을 새로고침해 확인해 주세요.')
}

export async function fetchThumbnail(datasetId: string, imageId: string, accessToken: string, signal: AbortSignal): Promise<Blob> {
  const response = await fetch(`/api/datasets/${encodeURIComponent(datasetId)}/images/${encodeURIComponent(imageId)}/thumbnail`, {
    headers: { Authorization: `Bearer ${accessToken}` }, signal,
  })
  if (response.status === 404) throw new Error('썸네일 없음')
  if (response.status === 401) throw new Error('다시 로그인해 주세요')
  if (response.status === 403) throw new Error('조회 권한 없음')
  if (!response.ok) throw new Error('썸네일 불러오기 실패')
  return response.blob()
}

export async function fetchImageContent(datasetId: string, imageId: string, accessToken: string, signal: AbortSignal): Promise<Blob> {
  const response = await fetch(`/api/datasets/${encodeURIComponent(datasetId)}/images/${encodeURIComponent(imageId)}/content`, {
    headers: { Authorization: `Bearer ${accessToken}` }, signal,
  })
  if (response.status === 401) throw new Error('로그인이 만료되었습니다. 다시 로그인해 주세요.')
  if (response.status === 403) throw new Error('이미지 조회 권한이 없거나 접근할 수 없는 데이터셋입니다.')
  if (response.status === 404) throw new Error('이미지를 찾을 수 없습니다. 목록을 새로고침해 주세요.')
  if (!response.ok) throw new Error('원본 이미지를 불러오지 못했습니다.')
  return response.blob()
}
