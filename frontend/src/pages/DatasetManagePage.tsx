import { Fragment, useEffect, useRef, useState, type PointerEvent } from 'react'
import { fetchThumbnail, type DatasetImage, type ImageDatasetTarget } from '../api/images'
import { fetchFileContent } from '../api/files'
import { entryApis, type EntryKind, type DatasetEntry, type EntryDescriptionChange } from '../api/datasetEntries'
import { fetchWorkspaces, type Workspace } from '../api/workspaces'
import DatasetUploadModal from './DatasetUploadModal'
import ImageDetailModal from './ImageDetailModal'

const dateTime = new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })
const imageLabels = ['선택', '썸네일', '원본 파일명', '버전', '형식', '원본 용량', '등록자', '최초 등록일시', '현재 버전 등록일시', '수정자', '수정일시']

function fileExtension(path: string) {
  const name = path.split('/').pop() ?? path
  const dot = name.lastIndexOf('.')
  return dot > 0 && dot < name.length - 1 ? name.substring(dot + 1).toUpperCase() : '확장자 없음'
}

function descriptionValue(value: string | null) { return value?.trim() ? value : null }
function descriptionEdited(image: DatasetEntry, drafts: Record<string, string>) {
  return drafts[image.entryId] !== undefined && descriptionValue(drafts[image.entryId]) !== descriptionValue(image.description)
}
export function getEntryDescriptionChanges(images: DatasetEntry[], drafts: Record<string, string>, selectedIds: string[]): EntryDescriptionChange[] {
  const selected = new Set(selectedIds)
  return images.filter(image => selected.has(image.entryId) && descriptionEdited(image, drafts))
    .map(image => ({ entryId: image.entryId, description: descriptionValue(drafts[image.entryId]) }))
}

function Thumbnail({ image, accessToken, onOpen, disabled }: { image: DatasetEntry; accessToken: string; onOpen: () => void; disabled: boolean }) {
  const [url, setUrl] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    if (!image.thumbnailUrl) return
    const controller = new AbortController()
    let objectUrl: string | undefined
    setUrl(null)
    setError(null)
    fetchThumbnail(image.datasetId, image.entryId, accessToken, controller.signal).then(blob => {
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
  }, [image.datasetId, image.entryId, image.thumbnailUrl, accessToken, attempt])
  if (!image.thumbnailUrl) return <span className="thumbnail-state">썸네일 없음</span>
  if (error) return <span className="thumbnail-state">{error}
    <button type="button" onClick={() => setAttempt(value => value + 1)}>재시도</button></span>
  return url ? <button className="image-thumbnail-button" type="button" disabled={disabled} onClick={onOpen} aria-haspopup="dialog" aria-label={`${image.originalFilename} 원본 이미지 보기`}>
    <img className="image-thumbnail" src={url} alt={`${image.originalFilename} 썸네일`} onError={() => setError('썸네일 불러오기 실패')} /></button>
    : <span className="thumbnail-state">불러오는 중…</span>
}

export default function DatasetManagePage({ accessToken }: { accessToken: string }) {
  const [initialId] = useState(() => new URLSearchParams(window.location.search).get('datasetId') ?? '')
  const [kind, setKind] = useState<EntryKind>(() => new URLSearchParams(window.location.search).get('tab') === 'files' ? 'files' : 'images')
  const api = entryApis[kind]
  const entryLabel = kind === 'images' ? '이미지' : '데이터 파일'
  const labels = kind === 'images' ? imageLabels : imageLabels.filter(label => label !== '썸네일').map(label => label === '형식' ? '파일 확장자' : label)
  const [datasets, setDatasets] = useState<ImageDatasetTarget[] | null>(null)
  const [workspaces, setWorkspaces] = useState<Workspace[]>([])
  const [workspaceId, setWorkspaceId] = useState('')
  const [datasetId, setDatasetId] = useState(initialId)
  const [images, setImages] = useState<DatasetEntry[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [selectionError, setSelectionError] = useState<string | null>(null)
  const [selectionAttempt, setSelectionAttempt] = useState(0)
  const [attempt, setAttempt] = useState(0)
  const [uploadMode, setUploadMode] = useState<'create' | 'replace' | null>(null)
  const [detailImage, setDetailImage] = useState<DatasetImage | null>(null)
  const [widths, setWidths] = useState(() => {
    const values = kind === 'images' ? [52, 100, 220, 80, 90, 100, 130, 180, 180, 130, 180] : [52, 220, 80, 90, 100, 130, 180, 180, 130, 180]
    const total = values.reduce((sum, value) => sum + value, 0)
    return values.map(value => value / total * 100)
  })
  const [selectedIds, setSelectedIds] = useState<string[]>([])
  const [deleting, setDeleting] = useState(false)
  const [saving, setSaving] = useState(false)
  const [drafts, setDrafts] = useState<Record<string, string>>({})
  const [expandedIds, setExpandedIds] = useState<string[]>([])
  const [saveResult, setSaveResult] = useState<{ message: string; failed: boolean } | null>(null)
  const [deleteResult, setDeleteResult] = useState<{ deleted: number; failures: string[] } | null>(null)
  const selectAll = useRef<HTMLInputElement>(null)
  const deletion = useRef<AbortController | null>(null)
  const saveRequest = useRef<AbortController | null>(null)
  const submitting = useRef(false)
  const drag = useRef<{ index: number; x: number; widths: number[] } | null>(null)
  const selected = datasets?.find(item => item.datasetId === datasetId)
  const busy = deleting || saving
  const dirtyCount = images?.filter(image => descriptionEdited(image, drafts)).length ?? 0
  const selectedChangeCount = images ? getEntryDescriptionChanges(images, drafts, selectedIds).length : 0

  useEffect(() => () => { deletion.current?.abort(); saveRequest.current?.abort() }, [])

  useEffect(() => {
    if (selectAll.current) selectAll.current.indeterminate = selectedIds.length > 0 && selectedIds.length < (images?.length ?? 0)
  }, [selectedIds, images])

  useEffect(() => {
    const controller = new AbortController()
    setDatasets(null)
    setSelectionError(null)
    Promise.all([api.targets(accessToken, controller.signal), fetchWorkspaces(accessToken, controller.signal)])
      .then(([items, spaces]) => {
        if (controller.signal.aborted) return
        setDatasets(items)
        setWorkspaces(spaces)
        const requestedId = new URLSearchParams(window.location.search).get('datasetId') ?? initialId
        const target = items.find(item => item.datasetId === requestedId)
        setDatasetId(target?.datasetId ?? '')
        setWorkspaceId(target?.workspaceId ?? '')
        if (requestedId && !target) setSelectionError('선택한 데이터셋에 접근할 수 없습니다. 다른 데이터셋을 선택해 주세요.')
      }).catch((reason: unknown) => {
        if (!controller.signal.aborted) setSelectionError(reason instanceof Error ? reason.message : '선택 목록을 불러오지 못했습니다.')
      })
    return () => controller.abort()
  }, [accessToken, selectionAttempt, initialId, api])

  useEffect(() => {
    const controller = new AbortController()
    setImages(null)
    setError(null)
    setSelectedIds([])
    setDeleteResult(null)
    setDrafts({})
    setExpandedIds([])
    setSaveResult(null)
    if (selected) api.list(selected.datasetId, accessToken, controller.signal).then(result => {
      if (!controller.signal.aborted) setImages(result)
    }).catch((reason: unknown) => {
      if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : '서버에 연결할 수 없습니다. 다시 시도해 주세요.')
    })
    return () => controller.abort()
  }, [selected, accessToken, attempt, api])

  function chooseDataset(id: string) {
    setDatasetId(id)
    setImages(null)
    setError(null)
    window.history.replaceState(null, '', `/datasets/manage?tab=${kind}${id ? `&datasetId=${encodeURIComponent(id)}` : ''}`)
  }

  function changeKind(next: EntryKind) {
    if (busy || next === kind) return
    window.history.replaceState(null, '', '/datasets/manage?tab=' + next + (datasetId ? '&datasetId=' + encodeURIComponent(datasetId) : ''))
    setImages(null)
    setDatasets(null)
    setDetailImage(null)
    setKind(next)
    const values = next === 'images' ? [52, 100, 220, 80, 90, 100, 130, 180, 180, 130, 180] : [52, 220, 80, 90, 100, 130, 180, 180, 130, 180]
    const total = values.reduce((sum, value) => sum + value, 0)
    setWidths(values.map(value => value / total * 100))
  }

  async function openEntry(entry: DatasetEntry) {
    if (kind === 'images') { setDetailImage({ ...entry, imageId: entry.entryId }); return }
    try {
      const blob = await fetchFileContent(entry.datasetId, entry.entryId, accessToken, new AbortController().signal)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = entry.originalFilename.split('/').pop() ?? entry.originalFilename
      document.body.append(link)
      link.click()
      link.remove()
      window.setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch (reason: unknown) {
      setSaveResult({ message: reason instanceof Error ? reason.message : '파일을 다운로드하지 못했습니다.', failed: true })
    }
  }

  function toggleDescription(entryId: string) {
    if (busy) return
    setExpandedIds(current => current.includes(entryId) ? current.filter(id => id !== entryId) : [...current, entryId])
  }

  async function handleSave() {
    if (submitting.current || !selected || !images || dirtyCount === 0) return
    const changes = getEntryDescriptionChanges(images, drafts, selectedIds)
    const unselectedChanges = dirtyCount - changes.length
    const confirmation = unselectedChanges > 0
      ? `선택하지 않은 변경 행 ${unselectedChanges}개의 편집 내용은 저장 후 초기화됩니다.\n저장하시겠습니까?`
      : '변경 내용을 저장하시겠습니까?'
    if (!window.confirm(confirmation)) return
    submitting.current = true
    setSaving(true)
    setSaveResult(null)
    setDeleteResult(null)
    const controller = new AbortController()
    saveRequest.current = controller
    try {
      const saved = changes.length ? await api.save(selected.datasetId, changes, accessToken, controller.signal) : []
      if (controller.signal.aborted) return
      const updated = new Map(saved.map(image => [image.entryId, image]))
      setImages(current => current?.map(image => updated.get(image.entryId) ?? image) ?? null)
      setDrafts({})
      setSelectedIds([])
      setSaveResult({ message: `${saved.length}개 행 저장 완료 · 선택되지 않은 행의 편집 내용을 초기화했습니다.`, failed: false })
    } catch (reason: unknown) {
      if (!controller.signal.aborted) setSaveResult({ message: reason instanceof Error ? reason.message : '설명을 저장하지 못했습니다.', failed: true })
    } finally {
      submitting.current = false
      if (!controller.signal.aborted) setSaving(false)
    }
  }

  async function handleDelete() {
    if (submitting.current || !selected || !images || selectedIds.length === 0) return
    const targets = images.filter(image => selectedIds.includes(image.entryId))
    if (!window.confirm(`선택한 ${entryLabel} ${targets.length}개를 삭제하시겠습니까? 목록에서 제외되며 기존 기록과 파일은 보관됩니다.`)) return
    submitting.current = true
    setDeleting(true)
    setDeleteResult(null)
    setSaveResult(null)
    const controller = new AbortController()
    deletion.current = controller
    const deletedIds: string[] = []
    const failedIds: string[] = []
    const failures: string[] = []
    try {
      for (const image of targets) {
        if (controller.signal.aborted) return
        try {
          await api.remove(selected.datasetId, image.entryId, accessToken, controller.signal)
          if (controller.signal.aborted) return
          deletedIds.push(image.entryId)
        } catch (reason: unknown) {
          if (controller.signal.aborted) return
          failedIds.push(image.entryId)
          failures.push(`${image.originalFilename}: ${reason instanceof Error ? reason.message : '삭제하지 못했습니다.'}`)
        }
      }
      setImages(current => current?.filter(image => !deletedIds.includes(image.entryId)) ?? null)
      setSelectedIds(failedIds)
      setDeleteResult({ deleted: deletedIds.length, failures })
    } finally {
      submitting.current = false
      if (!controller.signal.aborted) setDeleting(false)
    }
  }

  function resize(index: number, delta: number, current: number[]) {
    const minimum = Math.min(index === 0 ? 44 : 80, current[index])
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
    <p className="page-location">데이터셋 / 데이터셋 관리</p>
    <div className="dataset-list-heading"><h1 id="image-list-title">데이터셋 관리</h1>
      <a className="secondary-action" href="/datasets" aria-disabled={busy} onClick={event => { if (busy) event.preventDefault() }}>데이터셋 목록으로</a></div>
    <div className="management-tabs" role="tablist" aria-label="데이터 유형">
      {(['images', 'files'] as const).map(tab => <button key={tab} id={'dataset-tab-' + tab} type="button" role="tab"
        aria-selected={kind === tab} aria-controls="dataset-management-panel" disabled={busy} onClick={() => changeKind(tab)}
        onKeyDown={event => { if (['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) {
          event.preventDefault()
          const next = event.key === 'Home' ? 'images' : event.key === 'End' ? 'files' : kind === 'images' ? 'files' : 'images'
          changeKind(next)
          document.getElementById('dataset-tab-' + next)?.focus()
        } }}>{tab === 'images' ? '이미지 파일' : '데이터 파일'}</button>)}
    </div>
    <div id="dataset-management-panel" role="tabpanel" className="dataset-tab-panel" aria-labelledby={'dataset-tab-' + kind}>
    <p className="intro">{selected ? `${selected.workspaceName} / ${selected.name}` : '워크스페이스와 데이터셋을 선택해 파일을 확인하세요.'}</p>
    <p className="field-description">행을 클릭하면 아래에서 설명을 편집할 수 있습니다.</p>
    <div className="image-list-filters">
      <label>워크스페이스<select value={workspaceId} disabled={datasets === null || busy} onChange={event => {
        setWorkspaceId(event.target.value)
        chooseDataset('')
      }}><option value="">워크스페이스 선택</option>{workspaces.map(item => <option key={item.workspaceId} value={item.workspaceId}>{item.name} · {item.workspaceId.slice(-8)}</option>)}</select></label>
      <label>데이터셋<select value={datasetId} disabled={!workspaceId || datasets === null || busy} onChange={event => chooseDataset(event.target.value)}>
        <option value="">데이터셋 선택</option>{datasets?.filter(item => item.workspaceId === workspaceId).map(item => <option key={item.datasetId} value={item.datasetId}>{item.name} · {item.datasetId.slice(-8)}</option>)}
      </select></label>
      <button className="secondary-action" disabled={!selected || busy} onClick={() => setAttempt(value => value + 1)}>새로고침</button>
      <button className="primary-action" disabled={!selected || busy} onClick={() => setUploadMode('create')}>{entryLabel} 업로드</button>
      <button className="secondary-action" disabled={!selected || busy} onClick={() => {
        if (window.confirm('기존 파일을 덮어씁니다.\n이 데이터셋에서 원본 파일명이 같은 파일을 새 버전으로 교체합니다. 계속하시겠습니까?')) setUploadMode('replace')
      }}>{entryLabel} 수정 업로드</button>
    </div>
    <div className="dataset-list-actions"><span aria-live="polite">{selectedIds.length}개 선택 · 변경 {dirtyCount}개 · 저장 대상 {selectedChangeCount}개</span>
      <button className="primary-action" type="button" disabled={busy || dirtyCount === 0} onClick={handleSave}>{saving ? '저장 중…' : '저장'}</button>
      <button className="danger-action" type="button" disabled={busy || selectedIds.length === 0} onClick={handleDelete}>{deleting ? '삭제 중…' : '선택 삭제'}</button></div>
    {saveResult && <p role={saveResult.failed ? 'alert' : 'status'}>{saveResult.message}</p>}
    {deleteResult && <div className="dataset-delete-result" role={deleteResult.failures.length ? 'alert' : 'status'}>
      <p>{deleteResult.deleted}개 삭제 완료{deleteResult.failures.length > 0 && ` · ${deleteResult.failures.length}개 삭제 실패`}</p>
      {deleteResult.failures.length > 0 && <ul>{deleteResult.failures.map((message, index) => <li key={index}>{message}</li>)}</ul>}
    </div>}
    {selectionError && <div role="alert"><p>{selectionError}</p><button className="secondary-action" disabled={busy} onClick={() => setSelectionAttempt(value => value + 1)}>선택 목록 다시 불러오기</button></div>}
    {datasets === null ? !selectionError && <p role="status">선택 목록을 불러오고 있습니다.</p>
      : workspaces.length === 0 ? <p role="status">가입한 워크스페이스가 없습니다.</p>
      : !selected ? <p role="status">{workspaceId && !datasets.some(item => item.workspaceId === workspaceId) ? '이 워크스페이스에 데이터셋이 없습니다.' : '데이터셋을 선택해 주세요.'}</p>
      : error ? <div role="alert"><p>{error}</p><button className="secondary-action" onClick={() => setAttempt(value => value + 1)}>다시 시도</button></div>
      : images === null ? <p role="status">{entryLabel} 목록을 불러오고 있습니다.</p>
      : images.length === 0 ? <div className="dataset-list-state" role="status">등록된 {entryLabel} 항목이 없습니다.</div>
      : <div className="dataset-table-scroll" role="region" aria-label={`${entryLabel} 목록 표`} tabIndex={0}>
        <table className="dataset-table image-table"><caption>{entryLabel} {images.length}개 · 최신 등록순 · 일시는 현지 시간 기준</caption>
          <colgroup>{widths.map((width, i) => <col key={labels[i]} style={{ width: `${width}%` }} />)}</colgroup>
          <thead><tr>{labels.map((label, index) => <th key={label} scope="col" className={index === 0 ? 'selection-column' : undefined}>
            {index === 0 ? <input ref={selectAll} type="checkbox" aria-label={`${entryLabel} 전체 선택`} disabled={busy}
              checked={images.length > 0 && selectedIds.length === images.length}
              onChange={event => setSelectedIds(event.target.checked ? images.map(image => image.entryId) : [])} /> : label}{index < labels.length - 1 &&
            <span className="column-resize-handle" role="separator" aria-orientation="vertical" tabIndex={0} aria-label={`${label} 컬럼 너비 조절`}
              onPointerDown={event => startResize(event, index)} onPointerMove={event => {
                if (drag.current?.index === index) resize(index, event.clientX - drag.current.x, drag.current.widths)
              }} onPointerUp={event => { drag.current = null; if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId) }}
              onLostPointerCapture={() => { drag.current = null }} onKeyDown={event => {
                if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
                event.preventDefault()
                resize(index, event.key === 'ArrowRight' ? 16 : -16, Array.from(event.currentTarget.closest('tr')!.querySelectorAll('th'), th => th.getBoundingClientRect().width))
              }} />}</th>)}</tr></thead>
          <tbody>{images.map(image => <Fragment key={image.entryId}><tr tabIndex={0}
            className={`image-summary-row${descriptionEdited(image, drafts) ? ' image-row-edited' : ''}`}
            aria-expanded={expandedIds.includes(image.entryId)} aria-controls={`image-description-${image.entryId}`} aria-disabled={busy}
            onClick={event => { if (!(event.target as HTMLElement).closest('button, input, textarea, a, select')) toggleDescription(image.entryId) }}
            onKeyDown={event => {
              if (event.target === event.currentTarget && (event.key === 'Enter' || event.key === ' ')) {
                event.preventDefault()
                toggleDescription(image.entryId)
              }
            }}>
            <td className="selection-column"><input type="checkbox" disabled={busy} aria-label={`${image.originalFilename} 선택`} checked={selectedIds.includes(image.entryId)}
              onChange={event => { const checked = event.target.checked; setSelectedIds(current => checked ? [...current, image.entryId] : current.filter(id => id !== image.entryId)) }} /></td>
            {kind === 'images' && <td><Thumbnail image={image} accessToken={accessToken} disabled={busy} onOpen={() => { void openEntry(image) }} /></td>}
            <th scope="row"><button className="dataset-name-button" type="button" disabled={busy}
              aria-expanded={expandedIds.includes(image.entryId)} aria-controls={`image-description-${image.entryId}`}
              onClick={() => toggleDescription(image.entryId)}>{image.originalFilename}</button></th>
            <td>{image.versionCount}</td><td>{kind === 'images' ? image.mediaType.replace('image/', '').toUpperCase() : fileExtension(image.originalFilename)}</td>
            <td>{new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 1 }).format(image.sizeBytes / 1024)} KB</td>
            <td>{image.creatorUserId ?? '-'}<br />{image.creatorUsername ?? '-'}</td>
            <td>{image.firstRegisteredAt ? <time dateTime={image.firstRegisteredAt}>{dateTime.format(new Date(image.firstRegisteredAt))}</time> : '-'}</td>
            <td><time dateTime={image.createdAt}>{dateTime.format(new Date(image.createdAt))}</time></td>
            <td>{image.updaterUserId ?? '-'}<br />{image.updaterUsername ?? '-'}</td>
            <td><time dateTime={image.updatedAt}>{dateTime.format(new Date(image.updatedAt))}</time></td></tr>
            <tr id={`image-description-${image.entryId}`} hidden={!expandedIds.includes(image.entryId)}
              className={`image-description-row${descriptionEdited(image, drafts) ? ' image-row-edited' : ''}`}>
              <td colSpan={labels.length}>
                <div className="image-description-heading"><label htmlFor={`image-description-input-${image.entryId}`}>설명 · {image.originalFilename} <small>최대 2,000자</small></label>
                  <button type="button" className="secondary-action" disabled={busy} aria-haspopup="dialog" onClick={() => { void openEntry(image) }}>{kind === 'images' ? '원본 보기' : '파일 다운로드'}</button></div>
                <textarea id={`image-description-input-${image.entryId}`} className="image-description-input" rows={3} maxLength={2000} disabled={busy}
                  aria-label={`${image.originalFilename} 설명`} value={drafts[image.entryId] ?? image.description ?? ''} onChange={event => {
                    const description = event.target.value
                    setDrafts(current => ({ ...current, [image.entryId]: description }))
                    setSaveResult(null)
                    if (descriptionValue(description) !== descriptionValue(image.description)) setSelectedIds(current => current.includes(image.entryId) ? current : [...current, image.entryId])
                  }} />
              </td>
            </tr></Fragment>)}</tbody>
        </table>
      </div>}
    </div>
    {uploadMode && selected && <DatasetUploadModal kind={kind} key={`${selected.datasetId}-${uploadMode}`} dataset={selected} accessToken={accessToken}
      replaceExisting={uploadMode === 'replace'} onClose={() => setUploadMode(null)} onComplete={() => setAttempt(value => value + 1)} />}
    {detailImage && <ImageDetailModal image={detailImage} accessToken={accessToken} onClose={() => setDetailImage(null)} />}
  </section>
}
