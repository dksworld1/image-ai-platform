import { useEffect, useRef, useState, type ReactNode, type PointerEvent } from 'react'
import { createDatasetVersion, deleteDatasetVersion, fetchDatasetVersionDrafts, fetchDatasetVersions, fetchVersionEntries, fetchVersionContent, fetchVersionThumbnail, publishDatasetVersionDraft, saveDatasetVersionDraft, startDatasetVersionDraft, type DatasetVersion, type DatasetVersionDraft, type DatasetVersionEntry } from '../api/datasetVersions'

import DatasetFileGridScroll from './DatasetFileGridScroll'

const dateTime = new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })
const detailTabs = { images: '이미지', files: '데이터 파일' } as const

function VersionThumbnail({ datasetId, versionId, entry, accessToken }: {
  datasetId: string; versionId: string; entry: DatasetVersionEntry; accessToken: string
}) {
  const [url, setUrl] = useState<string | null>(null)
  const [unavailable, setUnavailable] = useState(false)
  useEffect(() => {
    const controller = new AbortController()
    let imageUrl: string | undefined
    fetchVersionThumbnail(datasetId, versionId, entry.entryId, accessToken, controller.signal).then(blob => {
      if (controller.signal.aborted) return
      imageUrl = URL.createObjectURL(blob)
      setUrl(imageUrl)
    }).catch(() => { if (!controller.signal.aborted) setUnavailable(true) })
    return () => {
      controller.abort()
      if (imageUrl) URL.revokeObjectURL(imageUrl)
    }
  }, [datasetId, versionId, entry.entryId, accessToken])
  if (unavailable) return <span className="thumbnail-state">썸네일 없음</span>
  return url ? <img className="image-thumbnail" src={url} alt={`${entry.originalFilename} 썸네일`} onError={() => setUnavailable(true)} />
    : <span className="thumbnail-state">불러오는 중…</span>
}

// Two tables in this panel share only their local column-resize rendering.
function VersionTable({ labels, children, caption, className = '', selectionHeader }: { labels: string[]; children: ReactNode; caption: string; className?: string; selectionHeader?: ReactNode }) {
  const [widths, setWidths] = useState(() => labels.map((_, index) => selectionHeader ? index === 0 ? 7 : 93 / (labels.length - 1) : 100 / labels.length))
  const drag = useRef<{ index: number; x: number; widths: number[] } | null>(null)
  function resize(index: number, delta: number, current: number[]) {
    const change = Math.max(Math.min(80, current[index]) - current[index], Math.min(delta, current[index + 1] - Math.min(80, current[index + 1])))
    const total = current.reduce((sum, value) => sum + value, 0)
    setWidths(current.map((value, i) => (value + (i === index ? change : i === index + 1 ? -change : 0)) / total * 100))
  }
  function start(event: PointerEvent<HTMLSpanElement>, index: number) {
    if (event.button !== 0) return
    event.preventDefault()
    drag.current = { index, x: event.clientX, widths: Array.from(event.currentTarget.closest('tr')!.querySelectorAll('th'), th => th.getBoundingClientRect().width) }
    event.currentTarget.setPointerCapture(event.pointerId)
  }
  const table = <table className="dataset-table version-table"><caption>{caption}</caption>
      <colgroup>{widths.map((width, index) => <col key={labels[index]} style={{ width: `${width}%` }} />)}</colgroup>
      <thead><tr>{labels.map((label, index) => <th key={label} scope="col">{index === 0 && selectionHeader ? selectionHeader : label}{index < labels.length - 1 &&
        <span className="column-resize-handle" role="separator" aria-orientation="vertical" tabIndex={0} aria-label={`${label} 컬럼 너비 조절`}
          onPointerDown={event => start(event, index)} onPointerMove={event => { if (drag.current?.index === index) resize(index, event.clientX - drag.current.x, drag.current.widths) }}
          onPointerUp={event => { drag.current = null; if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId) }} onLostPointerCapture={() => { drag.current = null }}
          onKeyDown={event => { if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') { event.preventDefault(); resize(index, event.key === 'ArrowRight' ? 16 : -16, Array.from(event.currentTarget.closest('tr')!.querySelectorAll('th'), th => th.getBoundingClientRect().width)) } }} />}</th>)}</tr></thead>
      <tbody>{children}</tbody>
  </table>
  if (selectionHeader) return <DatasetFileGridScroll label={caption}>{table}</DatasetFileGridScroll>
  return <div className={`dataset-table-scroll ${className}`} role="region" aria-label={caption} tabIndex={0}>{table}</div>
}

export default function DatasetVersionPanel({ datasetId, accessToken, refreshKey, onBusyChange, renderEditor }: {
  datasetId: string; accessToken: string; refreshKey: number; onBusyChange: (value: boolean) => void
  renderEditor: (workingDatasetId: string, onBusy: (value: boolean) => void, onDirty: (value: boolean) => void) => ReactNode
}) {
  const [versions, setVersions] = useState<DatasetVersion[] | null>(null)
  const [drafts, setDrafts] = useState<DatasetVersionDraft[]>([])
  const [draftId, setDraftId] = useState(() => new URLSearchParams(location.search).get('draftId') ?? (new URLSearchParams(location.search).get('mode') === 'edit' ? datasetId : ''))
  const [versionId, setVersionId] = useState(() => new URLSearchParams(location.search).get('versionId') ?? '')
  const showDetail = versionId !== '' && draftId === ''
  const [entries, setEntries] = useState<DatasetVersionEntry[] | null>(null)
  const [detailKind, setDetailKind] = useState<DatasetVersionEntry['kind']>('images')
  const [error, setError] = useState<string | null>(null)
  const [entryError, setEntryError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  const [entryAttempt, setEntryAttempt] = useState(0)
  const [changeNote, setChangeNote] = useState('')
  const [creating, setCreating] = useState(false)
  const [restoring, setRestoring] = useState(false)
  const [savingDraft, setSavingDraft] = useState(false)
  const editing = draftId !== ''
  const [draftName, setDraftName] = useState('')
  const [editorBusy, setEditorBusy] = useState(false)
  const [editorDirty, setEditorDirty] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const busy = creating || restoring || savingDraft || deleting || editorBusy
  const [message, setMessage] = useState<{ text: string; failed: boolean } | null>(null)
  const [downloading, setDownloading] = useState(false)
  const [selectedEntries, setSelectedEntries] = useState<Set<DatasetVersionEntry>>(new Set())
  const createRequest = useRef<AbortController | null>(null)
  const restoreRequest = useRef<AbortController | null>(null)
  const downloadRequest = useRef<AbortController | null>(null)
  const submitting = useRef(false)
  const selected = versions?.find(version => version.versionId === versionId)
  const draft = drafts.find(item => item.draftId === draftId)
  const visibleEntries = entries?.filter(entry => entry.kind === detailKind)
  const allEntriesSelected = !!visibleEntries?.length && selectedEntries.size === visibleEntries.length

  useEffect(() => {
    const controller = new AbortController()
    setVersions(null); setError(null)
    Promise.all([
      fetchDatasetVersions(datasetId, accessToken, controller.signal),
      fetchDatasetVersionDrafts(datasetId, accessToken, controller.signal),
    ]).then(([items, savedDrafts]) => {
      if (controller.signal.aborted) return
      setDrafts(savedDrafts); setVersions(items)
    }).catch((reason: unknown) => { if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : '버전 목록을 불러오지 못했습니다.') })
    return () => controller.abort()
  }, [datasetId, accessToken, refreshKey, attempt])
  useEffect(() => {
    const controller = new AbortController()
    setEntries(null); setEntryError(null)
    downloadRequest.current?.abort(); downloadRequest.current = null; setDownloading(false)
    setSelectedEntries(new Set())
    if (selected && showDetail) fetchVersionEntries(datasetId, selected.versionId, accessToken, controller.signal).then(items => {
      if (!controller.signal.aborted) setEntries(items)
    }).catch((reason: unknown) => { if (!controller.signal.aborted) setEntryError(reason instanceof Error ? reason.message : '버전 파일을 불러오지 못했습니다.') })
    return () => controller.abort()
  }, [datasetId, accessToken, selected, entryAttempt, showDetail])
  useEffect(() => () => { createRequest.current?.abort(); restoreRequest.current?.abort(); downloadRequest.current?.abort() }, [])

  useEffect(() => { onBusyChange(busy || editing) }, [busy, editing, onBusyChange])
  useEffect(() => {
    setDraftName(draft?.name ?? '')
    setChangeNote(draft?.changeNote ?? '')
  }, [draft])
  useEffect(() => { setEditorDirty(false) }, [draftId])
  useEffect(() => {
    const sync = () => {
      const params = new URLSearchParams(location.search)
      if (params.get('datasetId') !== datasetId) return
      setVersionId(params.get('versionId') ?? '')
      setDraftId(params.get('draftId') ?? (params.get('mode') === 'edit' ? datasetId : ''))
      setMessage(null)
      if (!params.has('versionId') && !params.has('draftId') && !params.has('mode')) setAttempt(value => value + 1)
    }
    window.addEventListener('popstate', sync)
    return () => window.removeEventListener('popstate', sync)
  }, [datasetId])
  function navigate(nextVersion = '', nextDraft = '', replace = false) {
    const url = new URL(window.location.href)
    url.searchParams.delete('mode')
    url.searchParams.delete('tab')
    if (nextVersion) url.searchParams.set('versionId', nextVersion)
    else url.searchParams.delete('versionId')
    if (nextDraft) url.searchParams.set('draftId', nextDraft)
    else url.searchParams.delete('draftId')
    if (replace) window.history.replaceState(null, '', url)
    else window.history.pushState(null, '', url)
    setVersionId(nextVersion)
    setDraftId(nextDraft)
  }
  async function create() {
    if (submitting.current || versions === null || editorDirty || editorBusy || (editing && !draft)) return
    submitting.current = true; setCreating(true); onBusyChange(true); setMessage(null)
    const controller = new AbortController(); createRequest.current = controller
    try {
      const version = draft
        ? await publishDatasetVersionDraft(datasetId, draft.draftId, changeNote, accessToken, controller.signal)
        : await createDatasetVersion(datasetId, changeNote, accessToken, controller.signal)
      if (controller.signal.aborted) return
      setVersions(current => [version, ...(current ?? []).filter(item => item.versionId !== version.versionId)])
      if (draft) setDrafts(current => current.filter(item => item.draftId !== draft.draftId))
      navigate(version.versionId, '', editing); setChangeNote(''); setMessage({ text: `버전 ${version.versionNumber} 생성 완료 · 이미지 ${version.imageCount}개 · 데이터 파일 ${version.fileCount}개`, failed: false })
    } catch (reason: unknown) {
      if (!controller.signal.aborted) setMessage({ text: reason instanceof Error ? reason.message : '버전을 생성하지 못했습니다.', failed: true })
    } finally {
      submitting.current = false
      if (!controller.signal.aborted) { setCreating(false); onBusyChange(false) }
    }
  }
  async function restore() {
    if (submitting.current || !selected || downloading) return
    submitting.current = true; setRestoring(true); onBusyChange(true); setMessage(null)
    const controller = new AbortController(); restoreRequest.current = controller
    try {
      const started = await startDatasetVersionDraft(datasetId, selected.versionId, accessToken, controller.signal)
      if (controller.signal.aborted) return
      setDrafts(current => [started, ...current])
      navigate(started.baseVersionId, started.draftId)
    } catch (reason: unknown) {
      if (!controller.signal.aborted) setMessage({ text: reason instanceof Error ? reason.message : '편집본을 준비하지 못했습니다.', failed: true })
    } finally {
      submitting.current = false
      if (!controller.signal.aborted) { setRestoring(false); onBusyChange(false) }
    }
  }
  async function saveDraft() {
    if (submitting.current || !draft || editorBusy) return
    submitting.current = true; setSavingDraft(true); setMessage(null)
    const controller = new AbortController(); createRequest.current = controller
    try {
      const savedDraft = await saveDatasetVersionDraft(datasetId, draft.draftId, draftName, changeNote, accessToken, controller.signal)
      if (controller.signal.aborted) return
      setDrafts(current => [savedDraft, ...current.filter(item => item.draftId !== savedDraft.draftId)])
      setMessage({ text: '편집본 이름과 변경 설명을 저장했습니다.', failed: false })
    } catch (reason: unknown) {
      if (!controller.signal.aborted) setMessage({ text: reason instanceof Error ? reason.message : '편집본을 저장하지 못했습니다.', failed: true })
    } finally {
      submitting.current = false
      if (!controller.signal.aborted) setSavingDraft(false)
    }
  }
  async function deleteVersion(item: DatasetVersion | DatasetVersionDraft) {
    if (submitting.current || busy) return
    const isDraft = 'draftId' in item
    const id = isDraft ? item.draftId : item.versionId
    const title = isDraft ? `편집본 ${item.name} (기준 버전 ${item.baseVersionNumber})` : `영구 버전 ${item.versionNumber} · ${item.name}`
    const creator = [item.creatorUserId ?? item.createdBy, item.creatorUsername].filter(Boolean).join(' · ')
    if (!window.confirm(`${title}\n이미지 ${item.imageCount}개 · 데이터 파일 ${item.fileCount}개\n생성자 ${creator}\n\n이 항목을 삭제하시겠습니까?`)) return
    const controller = new AbortController()
    createRequest.current = controller
    submitting.current = true
    setDeleting(true)
    setMessage(null)
    try {
      await deleteDatasetVersion(datasetId, id, isDraft, accessToken, controller.signal)
      if (controller.signal.aborted) return
      if (isDraft) setDrafts(current => current.filter(draft => draft.draftId !== id))
      else setVersions(current => current?.filter(version => version.versionId !== id) ?? null)
      setMessage({ text: `${title} 삭제 완료`, failed: false })
    } catch (reason: unknown) {
      if (!controller.signal.aborted) setMessage({ text: reason instanceof Error ? reason.message : '삭제 결과를 확인하지 못했습니다.', failed: true })
    } finally {
      submitting.current = false
      if (!controller.signal.aborted) setDeleting(false)
    }
  }
  async function downloadSelected() {
    if (!selected || downloadRequest.current || !visibleEntries || selectedEntries.size === 0) return
    const controller = new AbortController(); downloadRequest.current = controller; setDownloading(true); setMessage(null)
    const pending = new Set(selectedEntries)
    let failure: string | null = null
    try {
      for (const entry of visibleEntries) {
        if (!pending.has(entry)) continue
        const blob = await fetchVersionContent(datasetId, selected.versionId, entry, accessToken, controller.signal)
        if (controller.signal.aborted) return
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = entry.originalFilename.split('/').pop() ?? entry.originalFilename
        document.body.append(link)
        link.click()
        link.remove()
        window.setTimeout(() => URL.revokeObjectURL(url), 1000)
        pending.delete(entry)
      }
    } catch (reason: unknown) {
      failure = reason instanceof Error ? reason.message : '다운로드하지 못했습니다.'
    } finally {
      if (downloadRequest.current === controller && !controller.signal.aborted) {
        downloadRequest.current = null
        setDownloading(false)
        setSelectedEntries(pending)
        const started = selectedEntries.size - pending.size
        setMessage({
          text: failure ? `${started}개 다운로드 요청 · ${pending.size}개 남음. ${failure}` : `${started}개 파일의 다운로드를 브라우저에 요청했습니다.`,
          failed: failure !== null,
        })
      }
    }
  }

  return <div className="dataset-version-panel">
    <p className="field-description">영구 버전은 당시의 이미지·데이터 파일 목록과 설명을 고정합니다.</p>
    {!showDetail && (editing || (versions?.length === 0 && drafts.length === 0)) && <form className="dataset-version-create" onSubmit={event => { event.preventDefault(); void create() }}>
      {draft && <><label htmlFor="dataset-draft-name">편집본 이름</label>
        <input id="dataset-draft-name" maxLength={200} disabled={busy} value={draftName} onChange={event => setDraftName(event.target.value)} /></>}
      <label htmlFor="dataset-version-note">변경 설명 <small>선택 · 최대 2,000자</small></label>
      <input id="dataset-version-note" type="text" maxLength={2000} disabled={busy} value={changeNote} onChange={event => setChangeNote(event.target.value)} />
      {editing && draft && <button className="secondary-action" type="button" disabled={busy} onClick={() => { void saveDraft() }}>{savingDraft ? '저장 중…' : '편집본 저장'}</button>}
      <button className="primary-action" type="submit" disabled={busy || editorDirty || versions === null || (editing && !draft)}>{creating ? '버전 확정 중…' : editing ? '영구 버전 확정' : '최초 버전 생성'}</button>
    </form>}
    {message && <p role={message.failed ? 'alert' : 'status'}>{message.text}</p>}
    {editing && <>
      <p role="status">현재 편집본입니다. 파일을 편집하고 새 버전을 생성하세요. 확정된 기존 버전은 변경되지 않습니다.</p>
      {editorDirty && <p role="status">영구 버전을 확정하려면 먼저 그리드의 파일 설명 변경을 저장하세요.</p>}
      <button className="secondary-action version-back" type="button" disabled={busy || versions === null && !error} onClick={() => {
        if ((editorDirty || changeNote !== (draft?.changeNote ?? '') || draftName !== (draft?.name ?? '')) &&
          !window.confirm('저장하지 않은 설명 변경을 버리고 편집 화면을 나가시겠습니까?')) return
        navigate('', '', true)
        setEditorDirty(false); setMessage(null); setAttempt(value => value + 1)
      }}>버전 목록으로 돌아가기</button>
      {draft ? renderEditor(draft.workingDatasetId, setEditorBusy, setEditorDirty)
        : versions !== null && <p role="status">편집본을 찾을 수 없습니다.</p>}
    </>}
    {error ? <div role="alert"><p>{error}</p><button className="secondary-action" onClick={() => setAttempt(value => value + 1)}>버전 목록 다시 불러오기</button></div>
      : versions === null ? <p role="status">버전 목록을 불러오고 있습니다.</p>
      : versions.length === 0 && drafts.length === 0 && !editing ? <p role="status">목록에 표시할 버전이 없습니다.</p>
      : !showDetail && !editing && <VersionTable labels={['버전', '상태', '변경 설명', '이미지', '데이터 파일', '생성자', '생성일시', '삭제']} caption={`버전 목록 · 영구 ${versions.length}개`} className="dataset-version-list">
        {drafts.map(item => <tr key={item.draftId}>
          <th scope="row"><button className="dataset-name-button" type="button" disabled={busy} onClick={() => {
            navigate(item.baseVersionId, item.draftId); setMessage(null)
          }}>{item.name}</button><br /><small>기준 버전 {item.baseVersionNumber}</small></th>
          <td>편집 중</td><td className="version-description">{item.changeNote ?? '-'}</td>
          <td>{item.imageCount}</td><td>{item.fileCount}</td>
          <td>{item.creatorUserId ?? item.createdBy}<br />{item.creatorUsername ?? '-'}</td>
          <td><time dateTime={item.createdAt}>{dateTime.format(new Date(item.createdAt))}</time></td>
          <td><button className="secondary-action" type="button" disabled={busy} aria-label={`${item.name} 삭제`} onClick={() => { void deleteVersion(item) }}>삭제</button></td>
        </tr>)}
        {versions.map(version => <tr key={version.versionId} className={version.versionId === versionId ? 'dataset-version-selected' : undefined}>
          <th scope="row"><button className="dataset-name-button" type="button" disabled={busy} onClick={() => navigate(version.versionId)}>버전 {version.versionNumber}</button></th>
          <td>영구</td><td className="version-description">{version.changeNote ?? '-'}</td><td>{version.imageCount}</td><td>{version.fileCount}</td>
          <td>{version.creatorUserId ?? version.createdBy}<br />{version.creatorUsername ?? '-'}</td>
          <td><time dateTime={version.createdAt}>{dateTime.format(new Date(version.createdAt))}</time></td>
          <td><button className="secondary-action" type="button" disabled={busy} aria-label={`영구 버전 ${version.versionNumber} 삭제`} onClick={() => { void deleteVersion(version) }}>삭제</button></td>
        </tr>)}
      </VersionTable>}
    {showDetail && !selected && versions && <button className="secondary-action version-back" type="button" onClick={() => navigate('', '', true)}>버전 목록으로</button>}
    {showDetail && selected && <>
      <button className="secondary-action version-back" type="button" disabled={busy} onClick={() => {
        navigate('', '', true); setMessage(null); setAttempt(value => value + 1)
      }}>버전 목록으로</button>
      <button className="primary-action version-back" type="button" disabled={busy || downloading} onClick={() => { void restore() }}>{restoring ? '편집본 준비 중…' : '이 버전으로 편집'}</button>
      <div className="dataset-version-summary"><h2>버전 {selected.versionNumber} 상세</h2><p>데이터셋 이름: {selected.name}</p><p className="version-description">설명: {selected.description ?? '-'}</p><p className="field-description">버전 ID: {selected.versionId}</p></div>
      <div className="management-tabs" role="tablist" aria-label="버전 파일 유형">
        {(['images', 'files'] as const).map(kind => <button key={kind} id={`version-detail-tab-${kind}`} role="tab" type="button"
          aria-controls="version-detail-files" aria-selected={detailKind === kind} disabled={busy || downloading}
          onClick={() => { setDetailKind(kind); setSelectedEntries(new Set()) }}>{detailTabs[kind]}</button>)}
      </div>
      <div id="version-detail-files" className="dataset-version-detail-files" role="tabpanel" aria-labelledby={`version-detail-tab-${detailKind}`}>
      {!!visibleEntries?.length && <button className="secondary-action version-back" type="button"
        disabled={busy || downloading || selectedEntries.size === 0} onClick={() => { void downloadSelected() }}>
        {downloading ? '다운로드 요청 중…' : `선택 다운로드 (${selectedEntries.size})`}
      </button>}
      {entryError ? <div role="alert"><p>{entryError}</p><button className="secondary-action" onClick={() => setEntryAttempt(value => value + 1)}>버전 파일 다시 불러오기</button></div>
        : !visibleEntries ? <p role="status">고정된 파일 목록을 불러오고 있습니다.</p>
        : visibleEntries.length === 0 ? <p role="status">이 버전에 등록된 {detailTabs[detailKind]} 항목이 없습니다.</p>
        : <VersionTable key={detailKind} labels={['선택', ...(detailKind === 'images' ? ['썸네일'] : []), '원본 파일명', '파일 버전', '형식', '용량', '당시 설명']} caption={`버전 ${selected.versionNumber}의 ${detailTabs[detailKind]} ${visibleEntries.length}개`}
          selectionHeader={<input type="checkbox" aria-label="모든 파일 선택" checked={allEntriesSelected} disabled={busy || downloading}
            ref={checkbox => { if (checkbox) checkbox.indeterminate = selectedEntries.size > 0 && !allEntriesSelected }}
            onChange={event => setSelectedEntries(event.target.checked ? new Set(visibleEntries) : new Set())} />}>
          {visibleEntries.map(entry => <tr key={`${selected.versionId}-${entry.entryId}`}>
            <td><input type="checkbox" aria-label={`${entry.originalFilename} 선택`} disabled={busy || downloading} checked={selectedEntries.has(entry)}
              onChange={event => {
                const checked = event.target.checked
                setSelectedEntries(current => {
                  const next = new Set(current)
                  if (checked) next.add(entry)
                  else next.delete(entry)
                  return next
                })
              }} /></td>
            {detailKind === 'images' && <td><VersionThumbnail datasetId={datasetId} versionId={selected.versionId} entry={entry} accessToken={accessToken} /></td>}
            <th scope="row">{entry.originalFilename}</th><td>{entry.versionCount}</td><td>{entry.mediaType}</td>
            <td>{new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 1 }).format(entry.sizeBytes / 1024)} KB</td><td className="version-description">{entry.description ?? '-'}</td>
          </tr>)}
        </VersionTable>}
      </div>
    </>}
  </div>
}
