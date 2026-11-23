import { useEffect, useRef, useState, type ReactNode, type PointerEvent } from 'react'
import { createDatasetVersion, fetchDatasetVersions, fetchVersionEntries, fetchVersionContent, restoreDatasetVersion, type DatasetVersion, type DatasetVersionEntry } from '../api/datasetVersions'

const dateTime = new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })

// Two tables in this panel share only their local column-resize rendering.
function VersionTable({ labels, children, caption, className = '' }: { labels: string[]; children: ReactNode; caption: string; className?: string }) {
  const [widths, setWidths] = useState(() => labels.map(() => 100 / labels.length))
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
  return <div className={`dataset-table-scroll ${className}`} role="region" aria-label={caption} tabIndex={0}>
    <table className="dataset-table version-table"><caption>{caption}</caption>
      <colgroup>{widths.map((width, index) => <col key={labels[index]} style={{ width: `${width}%` }} />)}</colgroup>
      <thead><tr>{labels.map((label, index) => <th key={label} scope="col">{label}{index < labels.length - 1 &&
        <span className="column-resize-handle" role="separator" aria-orientation="vertical" tabIndex={0} aria-label={`${label} 컬럼 너비 조절`}
          onPointerDown={event => start(event, index)} onPointerMove={event => { if (drag.current?.index === index) resize(index, event.clientX - drag.current.x, drag.current.widths) }}
          onPointerUp={event => { drag.current = null; if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId) }} onLostPointerCapture={() => { drag.current = null }}
          onKeyDown={event => { if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') { event.preventDefault(); resize(index, event.key === 'ArrowRight' ? 16 : -16, Array.from(event.currentTarget.closest('tr')!.querySelectorAll('th'), th => th.getBoundingClientRect().width)) } }} />}</th>)}</tr></thead>
      <tbody>{children}</tbody>
    </table>
  </div>
}

export default function DatasetVersionPanel({ datasetId, accessToken, refreshKey, onBusyChange, onRestored, renderEditor }: {
  datasetId: string; accessToken: string; refreshKey: number; onBusyChange: (value: boolean) => void
  onRestored: (version: DatasetVersion) => void; renderEditor: (onBusy: (value: boolean) => void, onDirty: (value: boolean) => void) => ReactNode
}) {
  const [versions, setVersions] = useState<DatasetVersion[] | null>(null)
  const [versionId, setVersionId] = useState('')
  const [showDetail, setShowDetail] = useState(false)
  const [entries, setEntries] = useState<DatasetVersionEntry[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [entryError, setEntryError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  const [entryAttempt, setEntryAttempt] = useState(0)
  const [changeNote, setChangeNote] = useState('')
  const [creating, setCreating] = useState(false)
  const [restoring, setRestoring] = useState(false)
  const [editing, setEditing] = useState(() => new URLSearchParams(window.location.search).get('mode') === 'edit')
  const [editorBusy, setEditorBusy] = useState(false)
  const [editorDirty, setEditorDirty] = useState(false)
  const busy = creating || restoring || editorBusy
  const [message, setMessage] = useState<{ text: string; failed: boolean } | null>(null)
  const [downloading, setDownloading] = useState(false)
  const createRequest = useRef<AbortController | null>(null)
  const restoreRequest = useRef<AbortController | null>(null)
  const downloadRequest = useRef<AbortController | null>(null)
  const submitting = useRef(false)
  const selected = versions?.find(version => version.versionId === versionId)

  useEffect(() => {
    const controller = new AbortController()
    setVersions(null); setError(null)
    fetchDatasetVersions(datasetId, accessToken, controller.signal).then(items => {
      if (controller.signal.aborted) return
      setVersions(items); setVersionId(current => items.some(item => item.versionId === current) ? current : items[0]?.versionId ?? '')
    }).catch((reason: unknown) => { if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : '버전 목록을 불러오지 못했습니다.') })
    return () => controller.abort()
  }, [datasetId, accessToken, refreshKey, attempt])
  useEffect(() => {
    const controller = new AbortController()
    setEntries(null); setEntryError(null)
    downloadRequest.current?.abort(); setDownloading(false)
    if (selected && showDetail) fetchVersionEntries(datasetId, selected.versionId, accessToken, controller.signal).then(items => {
      if (!controller.signal.aborted) setEntries(items)
    }).catch((reason: unknown) => { if (!controller.signal.aborted) setEntryError(reason instanceof Error ? reason.message : '버전 파일을 불러오지 못했습니다.') })
    return () => controller.abort()
  }, [datasetId, accessToken, selected, entryAttempt, showDetail])
  useEffect(() => () => { createRequest.current?.abort(); restoreRequest.current?.abort(); downloadRequest.current?.abort() }, [])

  useEffect(() => { onBusyChange(busy || editing) }, [busy, editing, onBusyChange])
  function editingUrl(value: boolean) {
    const url = new URL(window.location.href)
    if (value) url.searchParams.set('mode', 'edit')
    else { url.searchParams.delete('mode'); url.searchParams.delete('tab') }
    window.history.replaceState(null, '', url.pathname + url.search)
  }
  async function create() {
    if (submitting.current || versions === null || editorDirty || editorBusy) return
    submitting.current = true; setCreating(true); onBusyChange(true); setMessage(null)
    const controller = new AbortController(); createRequest.current = controller
    try {
      const version = await createDatasetVersion(datasetId, changeNote, accessToken, controller.signal)
      if (controller.signal.aborted) return
      setVersions(current => [version, ...(current ?? []).filter(item => item.versionId !== version.versionId)])
      setVersionId(version.versionId); setShowDetail(true); setChangeNote(''); setEditing(false); editingUrl(false); setMessage({ text: `버전 ${version.versionNumber} 생성 완료 · 이미지 ${version.imageCount}개 · 데이터 파일 ${version.fileCount}개`, failed: false })
    } catch (reason: unknown) {
      if (!controller.signal.aborted) setMessage({ text: reason instanceof Error ? reason.message : '버전을 생성하지 못했습니다.', failed: true })
    } finally {
      submitting.current = false
      if (!controller.signal.aborted) { setCreating(false); onBusyChange(false) }
    }
  }
  async function restore() {
    if (submitting.current || !selected || downloading) return
    if (!window.confirm(`버전 ${selected.versionNumber}의 구성으로 현재 편집본 전체를 교체하시겠습니까?\n공유된 현재 편집본의 파일 목록과 이름·설명이 바뀝니다. 버전으로 확정하지 않은 변경은 편집 목록에서 제외됩니다. 기존 확정 버전과 파일 바이트는 보존됩니다.\n적용 후 파일을 수정하고 새 버전을 별도로 생성해야 합니다.`)) return
    submitting.current = true; setRestoring(true); onBusyChange(true); setMessage(null)
    const controller = new AbortController(); restoreRequest.current = controller
    try {
      const version = await restoreDatasetVersion(datasetId, selected.versionId, accessToken, controller.signal)
      if (controller.signal.aborted) return
      onRestored(version); setEditing(true); editingUrl(true); setShowDetail(false)
      setMessage({ text: `버전 ${version.versionNumber}의 구성을 현재 편집본에 적용했습니다. 파일 탭에서 수정한 뒤 새 버전을 생성하세요. 새 버전은 아직 만들어지지 않았습니다.`, failed: false })
    } catch (reason: unknown) {
      if (!controller.signal.aborted) setMessage({ text: reason instanceof Error ? reason.message : '편집본을 준비하지 못했습니다.', failed: true })
    } finally {
      submitting.current = false
      if (!controller.signal.aborted) { setRestoring(false); onBusyChange(false) }
    }
  }
  async function download(entry: DatasetVersionEntry) {
    if (!selected || downloading) return
    const controller = new AbortController(); downloadRequest.current = controller; setDownloading(true); setMessage(null)
    try {
      const blob = await fetchVersionContent(datasetId, selected.versionId, entry, accessToken, controller.signal)
      if (controller.signal.aborted) return
      const url = URL.createObjectURL(blob); const link = document.createElement('a')
      link.href = url; link.download = entry.originalFilename.split('/').pop() ?? entry.originalFilename
      document.body.append(link); link.click(); link.remove(); window.setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch (reason: unknown) { if (!controller.signal.aborted) setMessage({ text: reason instanceof Error ? reason.message : '다운로드하지 못했습니다.', failed: true }) }
    finally { if (!controller.signal.aborted) setDownloading(false) }
  }

  return <div className="dataset-version-panel">
    <p className="field-description">버전은 이미지·데이터 파일 전체 목록과 당시 설명을 고정합니다. 파일 바이트는 재사용하며 확정 버전은 수정·삭제할 수 없습니다.</p>
    {!showDetail && (editing || versions?.length === 0) && <form className="dataset-version-create" onSubmit={event => { event.preventDefault(); void create() }}>
      <label htmlFor="dataset-version-note">변경 설명 <small>선택 · 최대 2,000자</small></label>
      <input id="dataset-version-note" type="text" maxLength={2000} disabled={busy} value={changeNote} onChange={event => setChangeNote(event.target.value)} />
      <button className="primary-action" type="submit" disabled={busy || editorDirty || versions === null}>{creating ? '버전 생성 중…' : editing ? '새 버전 생성' : '최초 버전 생성'}</button>
    </form>}
    {message && <p role={message.failed ? 'alert' : 'status'}>{message.text}</p>}
    {editing && <>
      <p role="status">현재 편집본입니다. 파일을 편집하고 새 버전을 생성하세요. 확정된 기존 버전은 변경되지 않습니다.</p>
      {editorDirty && <p role="status">설명 변경을 먼저 저장해야 새 버전을 생성할 수 있습니다.</p>}
      <button className="secondary-action version-back" type="button" disabled={busy} onClick={() => {
        if (editorDirty && !window.confirm('저장하지 않은 설명 편집을 버리고 버전 목록으로 돌아가시겠습니까?')) return
        setEditing(false); editingUrl(false); setEditorDirty(false); setMessage(null)
      }}>편집 종료·버전 목록</button>
      {renderEditor(setEditorBusy, setEditorDirty)}
    </>}
    {error ? <div role="alert"><p>{error}</p><button className="secondary-action" onClick={() => setAttempt(value => value + 1)}>버전 목록 다시 불러오기</button></div>
      : versions === null ? <p role="status">버전 목록을 불러오고 있습니다.</p>
      : versions.length === 0 && !editing ? <p role="status">확정된 버전이 없습니다. 현재 상태로 첫 버전을 생성하세요.</p>
      : !showDetail && !editing && <VersionTable labels={['버전', '변경 설명', '이미지', '데이터 파일', '생성자', '생성일시']} caption={`확정 버전 ${versions.length}개 · 최신 버전순`} className="dataset-version-list">
        {versions.map(version => <tr key={version.versionId} className={version.versionId === versionId ? 'dataset-version-selected' : undefined}>
          <th scope="row"><button className="dataset-name-button" type="button" disabled={busy} aria-pressed={version.versionId === versionId} onClick={() => { setVersionId(version.versionId); setShowDetail(true) }}>버전 {version.versionNumber}</button></th>
          <td className="version-description">{version.changeNote ?? '-'}</td><td>{version.imageCount}</td><td>{version.fileCount}</td>
          <td>{version.creatorUserId ?? version.createdBy}<br />{version.creatorUsername ?? '-'}</td>
          <td><time dateTime={version.createdAt}>{dateTime.format(new Date(version.createdAt))}</time></td>
        </tr>)}
      </VersionTable>}
    {showDetail && selected && <>
      <button className="secondary-action version-back" type="button" disabled={busy} onClick={() => { setShowDetail(false); setMessage(null) }}>버전 목록으로</button>
      <button className="primary-action version-back" type="button" disabled={busy || downloading} onClick={() => { void restore() }}>{restoring ? '편집본 준비 중…' : '이 버전으로 편집'}</button>
      <div className="dataset-version-summary"><h2>버전 {selected.versionNumber} 상세</h2><p>데이터셋 이름: {selected.name}</p><p className="version-description">설명: {selected.description ?? '-'}</p><p className="field-description">버전 ID: {selected.versionId}</p></div>
      {entryError ? <div role="alert"><p>{entryError}</p><button className="secondary-action" onClick={() => setEntryAttempt(value => value + 1)}>버전 파일 다시 불러오기</button></div>
        : entries === null ? <p role="status">고정된 파일 목록을 불러오고 있습니다.</p>
        : entries.length === 0 ? <p role="status">이 버전은 빈 데이터셋입니다.</p>
        : <VersionTable labels={['유형', '원본 파일명', '파일 버전', '형식', '용량', '당시 설명', '다운로드']} caption={`버전 ${selected.versionNumber}의 파일 ${entries.length}개`} className="dataset-version-entries">
          {entries.map(entry => <tr key={`${entry.kind}-${entry.entryId}`}><td>{entry.kind === 'images' ? '이미지' : '데이터 파일'}</td>
            <th scope="row">{entry.originalFilename}</th><td>{entry.versionCount}</td><td>{entry.mediaType}</td>
            <td>{new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 1 }).format(entry.sizeBytes / 1024)} KB</td><td className="version-description">{entry.description ?? '-'}</td>
            <td><button className="secondary-action" type="button" disabled={busy || downloading} onClick={() => { void download(entry) }}>다운로드</button></td>
          </tr>)}
        </VersionTable>}
    </>}
  </div>
}
