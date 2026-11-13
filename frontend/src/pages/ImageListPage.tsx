import { useEffect, useRef, useState, type PointerEvent } from 'react'
import { fetchImageDatasetTargets, fetchImages, fetchThumbnail, type DatasetImage, type ImageDatasetTarget } from '../api/images'
import { fetchWorkspaces, type Workspace } from '../api/workspaces'
import ImageUploadModal from './ImageUploadModal'
import ImageDetailModal from './ImageDetailModal'

const dateTime = new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })
const labels = ['썸네일', '원본 파일명', '형식', '원본 용량', '등록자', '등록일시', '수정자', '수정일시']

function Thumbnail({ image, accessToken, onOpen }: { image: DatasetImage; accessToken: string; onOpen: () => void }) {
  const [url, setUrl] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    if (!image.thumbnailUrl) return
    const controller = new AbortController()
    let objectUrl: string | undefined
    setUrl(null)
    setError(null)
    fetchThumbnail(image.datasetId, image.imageId, accessToken, controller.signal).then(blob => {
      if (controller.signal.aborted) return
      objectUrl = URL.createObjectURL(blob)
      setUrl(objectUrl)
    }).catch((reason: unknown) => {
      if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : '썸네일 불러오기 실패')
    })
    return () => {
      controller.abort()
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [image.datasetId, image.imageId, image.thumbnailUrl, accessToken, attempt])
  if (!image.thumbnailUrl) return <span className="thumbnail-state">썸네일 없음</span>
  if (error) return <span className="thumbnail-state">{error}
    <button type="button" onClick={() => setAttempt(value => value + 1)}>재시도</button></span>
  return url ? <button className="image-thumbnail-button" type="button" onClick={onOpen} aria-haspopup="dialog" aria-label={`${image.originalFilename} 원본 이미지 보기`}>
    <img className="image-thumbnail" src={url} alt={`${image.originalFilename} 썸네일`} onError={() => setError('썸네일 불러오기 실패')} /></button>
    : <span className="thumbnail-state">불러오는 중…</span>
}

export default function ImageListPage({ accessToken }: { accessToken: string }) {
  const [initialId] = useState(() => new URLSearchParams(window.location.search).get('datasetId') ?? '')
  const [datasets, setDatasets] = useState<ImageDatasetTarget[] | null>(null)
  const [workspaces, setWorkspaces] = useState<Workspace[]>([])
  const [workspaceId, setWorkspaceId] = useState('')
  const [datasetId, setDatasetId] = useState(initialId)
  const [images, setImages] = useState<DatasetImage[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [selectionError, setSelectionError] = useState<string | null>(null)
  const [selectionAttempt, setSelectionAttempt] = useState(0)
  const [attempt, setAttempt] = useState(0)
  const [uploadMode, setUploadMode] = useState<'create' | 'replace' | null>(null)
  const [detailImage, setDetailImage] = useState<DatasetImage | null>(null)
  const [widths, setWidths] = useState([8, 22, 8, 10, 12, 14, 12, 14])
  const drag = useRef<{ index: number; x: number; widths: number[] } | null>(null)
  const selected = datasets?.find(item => item.datasetId === datasetId)

  useEffect(() => {
    const controller = new AbortController()
    setDatasets(null)
    setSelectionError(null)
    Promise.all([fetchImageDatasetTargets(accessToken, controller.signal), fetchWorkspaces(accessToken, controller.signal)])
      .then(([items, spaces]) => {
        if (controller.signal.aborted) return
        setDatasets(items)
        setWorkspaces(spaces)
        const target = items.find(item => item.datasetId === initialId)
        setDatasetId(target?.datasetId ?? '')
        setWorkspaceId(target?.workspaceId ?? '')
        if (initialId && !target) setSelectionError('선택한 데이터셋에 접근할 수 없습니다. 다른 데이터셋을 선택해 주세요.')
      }).catch((reason: unknown) => {
        if (!controller.signal.aborted) setSelectionError(reason instanceof Error ? reason.message : '선택 목록을 불러오지 못했습니다.')
      })
    return () => controller.abort()
  }, [accessToken, selectionAttempt, initialId])

  useEffect(() => {
    const controller = new AbortController()
    setImages(null)
    setError(null)
    if (selected) fetchImages(selected.datasetId, accessToken, controller.signal).then(result => {
      if (!controller.signal.aborted) setImages(result)
    }).catch((reason: unknown) => {
      if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : '서버에 연결할 수 없습니다. 다시 시도해 주세요.')
    })
    return () => controller.abort()
  }, [selected, accessToken, attempt])

  function chooseDataset(id: string) {
    setDatasetId(id)
    setImages(null)
    setError(null)
    window.history.replaceState(null, '', `/datasets/images${id ? `?datasetId=${encodeURIComponent(id)}` : ''}`)
  }

  function resize(index: number, delta: number, current: number[]) {
    const minimum = Math.min(80, current[index])
    const nextMinimum = Math.min(80, current[index + 1])
    const change = Math.max(minimum - current[index], Math.min(delta, current[index + 1] - nextMinimum))
    const total = current.reduce((sum, value) => sum + value, 0)
    setWidths(current.map((value, i) => (value + (i === index ? change : i === index + 1 ? -change : 0)) / total * 100))
  }
  function startResize(event: PointerEvent<HTMLSpanElement>, index: number) {
    if (event.button !== 0) return
    event.preventDefault()
    drag.current = { index, x: event.clientX, widths: Array.from(event.currentTarget.closest('tr')!.querySelectorAll('th'), th => th.getBoundingClientRect().width) }
    event.currentTarget.setPointerCapture(event.pointerId)
  }

  return <section className="dataset-list" aria-labelledby="image-list-title">
    <p className="page-location">데이터셋 / 이미지 관리</p>
    <div className="dataset-list-heading"><h1 id="image-list-title">이미지 관리</h1>
      <a className="secondary-action" href="/datasets">데이터셋 목록으로</a></div>
    <p className="intro">{selected ? `${selected.workspaceName} / ${selected.name}` : '워크스페이스와 데이터셋을 선택해 이미지를 확인하세요.'}</p>
    <div className="image-list-filters">
      <label>워크스페이스<select value={workspaceId} disabled={datasets === null} onChange={event => {
        setWorkspaceId(event.target.value)
        chooseDataset('')
      }}><option value="">워크스페이스 선택</option>{workspaces.map(item => <option key={item.workspaceId} value={item.workspaceId}>{item.name} · {item.workspaceId.slice(-8)}</option>)}</select></label>
      <label>데이터셋<select value={datasetId} disabled={!workspaceId || datasets === null} onChange={event => chooseDataset(event.target.value)}>
        <option value="">데이터셋 선택</option>{datasets?.filter(item => item.workspaceId === workspaceId).map(item => <option key={item.datasetId} value={item.datasetId}>{item.name} · {item.datasetId.slice(-8)}</option>)}
      </select></label>
      <button className="secondary-action" disabled={!selected} onClick={() => setAttempt(value => value + 1)}>새로고침</button>
      <button className="primary-action" disabled={!selected} onClick={() => setUploadMode('create')}>이미지 업로드</button>
      <button className="secondary-action" disabled={!selected} onClick={() => {
        if (window.confirm('기존 파일을 덮어씁니다.\n이 데이터셋에서 원본 파일명이 같은 이미지를 새 버전으로 교체합니다. 계속하시겠습니까?')) setUploadMode('replace')
      }}>이미지 수정 업로드</button>
    </div>
    {selectionError && <div role="alert"><p>{selectionError}</p><button className="secondary-action" onClick={() => setSelectionAttempt(value => value + 1)}>선택 목록 다시 불러오기</button></div>}
    {datasets === null ? !selectionError && <p role="status">선택 목록을 불러오고 있습니다.</p>
      : workspaces.length === 0 ? <p role="status">가입한 워크스페이스가 없습니다.</p>
      : !selected ? <p role="status">{workspaceId && !datasets.some(item => item.workspaceId === workspaceId) ? '이 워크스페이스에 데이터셋이 없습니다.' : '데이터셋을 선택해 주세요.'}</p>
      : error ? <div role="alert"><p>{error}</p><button className="secondary-action" onClick={() => setAttempt(value => value + 1)}>다시 시도</button></div>
      : images === null ? <p role="status">이미지 목록을 불러오고 있습니다.</p>
      : images.length === 0 ? <div className="dataset-list-state" role="status">등록된 이미지가 없습니다.</div>
      : <div className="dataset-table-scroll" role="region" aria-label="이미지 목록 표" tabIndex={0}>
        <table className="dataset-table image-table"><caption>이미지 {images.length}개 · 최신 등록순 · 일시는 현지 시간 기준</caption>
          <colgroup>{widths.map((width, i) => <col key={labels[i]} style={{ width: `${width}%` }} />)}</colgroup>
          <thead><tr>{labels.map((label, index) => <th key={label} scope="col">{label}{index < labels.length - 1 &&
            <span className="column-resize-handle" role="separator" aria-orientation="vertical" tabIndex={0} aria-label={`${label} 컬럼 너비 조절`}
              onPointerDown={event => startResize(event, index)} onPointerMove={event => {
                if (drag.current?.index === index) resize(index, event.clientX - drag.current.x, drag.current.widths)
              }} onPointerUp={event => { drag.current = null; if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId) }}
              onLostPointerCapture={() => { drag.current = null }} onKeyDown={event => {
                if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
                event.preventDefault()
                resize(index, event.key === 'ArrowRight' ? 16 : -16, Array.from(event.currentTarget.closest('tr')!.querySelectorAll('th'), th => th.getBoundingClientRect().width))
              }} />}</th>)}</tr></thead>
          <tbody>{images.map(image => <tr key={image.imageId}><td><Thumbnail image={image} accessToken={accessToken} onOpen={() => setDetailImage(image)} /></td>
            <th scope="row"><button className="dataset-name-button" type="button" aria-haspopup="dialog" onClick={() => setDetailImage(image)}>{image.originalFilename}</button></th><td>{image.mediaType.replace('image/', '').toUpperCase()}</td>
            <td>{new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 1 }).format(image.sizeBytes / 1024)} KB</td>
            <td>{image.creatorUserId ?? '-'}<br />{image.creatorUsername ?? '-'}</td>
            <td><time dateTime={image.createdAt}>{dateTime.format(new Date(image.createdAt))}</time></td>
            <td>{image.updaterUserId ?? '-'}<br />{image.updaterUsername ?? '-'}</td>
            <td><time dateTime={image.updatedAt}>{dateTime.format(new Date(image.updatedAt))}</time></td></tr>)}</tbody>
        </table>
      </div>}
    {uploadMode && selected && <ImageUploadModal key={`${selected.datasetId}-${uploadMode}`} dataset={selected} accessToken={accessToken}
      replaceExisting={uploadMode === 'replace'} onClose={() => setUploadMode(null)} onComplete={() => setAttempt(value => value + 1)} />}
    {detailImage && <ImageDetailModal image={detailImage} accessToken={accessToken} onClose={() => setDetailImage(null)} />}
  </section>
}
