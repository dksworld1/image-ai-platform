import { useEffect, useRef, useState } from 'react'
import { ImageUploadError, uploadImage, type ImageDatasetTarget } from '../api/images'
import { FileUploadError, uploadFile } from '../api/files'
import type { EntryKind } from '../api/datasetEntries'
import { droppedUploadFiles } from './droppedUploadFiles'

type UploadItem = { id: number; file: File; description: string; descriptionOpen?: boolean; status: '대기' | '업로드 중' | '성공' | '실패' | '미전송'; message?: string }

export default function DatasetUploadModal({ dataset, accessToken, onClose, onComplete, replaceExisting = false, kind = 'images' }: {
  dataset: ImageDatasetTarget; accessToken: string; onClose: () => void; onComplete: () => void; replaceExisting?: boolean; kind?: EntryKind
}) {
  const entryLabel = kind === 'images' ? '이미지' : '데이터 파일'
  const upload = kind === 'images' ? uploadImage : uploadFile
  const dialog = useRef<HTMLDialogElement>(null)
  const input = useRef<HTMLInputElement>(null)
  const folderInput = useRef<HTMLInputElement>(null)
  const dropDepth = useRef(0)
  const readingDrop = useRef(false)
  const mounted = useRef(true)
  const sequence = useRef(0)
  const request = useRef<AbortController | null>(null)
  const submitting = useRef(false)
  const [items, setItems] = useState<UploadItem[]>([])
  const [running, setRunning] = useState(false)
  const [finished, setFinished] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [reading, setReading] = useState(false)
  const [dragging, setDragging] = useState(false)
  const busy = running || reading

  useEffect(() => {
    mounted.current = true
    if (dialog.current && !dialog.current.open) dialog.current.showModal()
    return () => { mounted.current = false; request.current?.abort() }
  }, [])

  function addFiles(files: File[]) {
    if (submitting.current || finished) return
    setItems(current => [...current, ...files.map(file => ({ id: sequence.current++, file, description: '', status: '대기' as const }))])
  }

  async function startUpload() {
    if (submitting.current || readingDrop.current || finished || items.length === 0) return
    submitting.current = true
    setRunning(true)
    setNotice(null)
    const controller = new AbortController()
    request.current = controller
    function update(id: number, status: UploadItem['status'], message?: string) {
      setItems(current => current.map(item => item.id === id ? { ...item, status, message } : item))
    }
    try {
      for (const item of items) {
        if (controller.signal.aborted) return
        update(item.id, '업로드 중')
        try {
          await upload(dataset.datasetId, item.file, accessToken, controller.signal, replaceExisting, item.description)
          if (controller.signal.aborted) return
          update(item.id, '성공')
        } catch (reason: unknown) {
          if (controller.signal.aborted) return
          update(item.id, '실패', reason instanceof Error ? reason.message : '등록 결과를 확인할 수 없습니다. 목록을 확인해 주세요.')
          if ((reason instanceof ImageUploadError || reason instanceof FileUploadError) && (reason.status === 401 || reason.status === 403)) {
            setNotice('인증 또는 권한 문제로 나머지 파일의 전송을 중단했습니다.')
            setItems(current => current.map(target => target.status === '대기' ? { ...target, status: '미전송' } : target))
            break
          }
        }
      }
      setFinished(true)
      onComplete()
    } finally {
      submitting.current = false
      if (!controller.signal.aborted) setRunning(false)
    }
  }

  const successCount = items.filter(item => item.status === '성공').length
  const failedCount = items.filter(item => item.status === '실패').length
  const pendingCount = items.filter(item => item.status === '대기' || item.status === '미전송').length

  return <dialog ref={dialog} className="dataset-detail-modal image-upload-modal" aria-labelledby="image-upload-title"
    onClose={onClose} onCancel={event => { if (submitting.current || readingDrop.current) event.preventDefault() }}>
    <div className="dataset-detail-heading"><h2 id="image-upload-title">{entryLabel}{replaceExisting ? ' 수정 업로드' : ' 업로드'}</h2>
      <button type="button" className="secondary-action" disabled={busy} onClick={() => dialog.current?.close()}>닫기</button></div>
    <p className="field-description">파일 이름을 클릭하면 설명 입력란이 열립니다. 다시 클릭하면 접히며, 입력한 내용은 유지됩니다.</p>
    <p className="field-description">폴더 선택 시 하위 폴더의 파일도 포함합니다. 선택한 폴더 이름부터의 경로를 원본 파일명으로 저장합니다. 빈 폴더는 저장하지 않습니다.</p>
    <p className="upload-target"><strong>{dataset.name}</strong><span>{dataset.workspaceName}</span><small>{dataset.datasetId}</small></p>
    {replaceExisting && <p className="image-replace-warning" role="alert"><strong>기존 파일을 덮어씁니다.</strong><br />
      이 데이터셋에 같은 원본 파일명이 있으면 해당 탭의 파일을 새 버전으로 교체합니다. 기존 기록과 파일은 보관하며, 같은 이름이 없으면 새로 등록합니다.</p>}
    <p id="image-upload-help" className="field-description">{kind === 'images' ? 'PNG·JPEG·GIF·BMP 파일을 선택하세요. 썸네일은 자동 생성됩니다.' : '이미지를 포함한 모든 형식의 파일을 원본 바이너리로 저장합니다. 썸네일은 생성하지 않습니다.'} 기본 파일당 10MB이며 실제 제한은 서버 설정에 따릅니다.</p>
    <p className="field-description">워크스페이스 운영자 또는 데이터셋 생성자에게 허용되며 {entryLabel} {replaceExisting ? '수정 권한이 필요합니다. 새 이름의 파일을 등록하려면 업로드 권한도 필요합니다.' : '업로드 권한이 필요합니다.'} 최종 권한은 서버에서 확인합니다.</p>
    {!finished && <>
      <div className="upload-pick-actions">
        <button type="button" className="primary-action" disabled={busy} onClick={() => input.current?.click()}>파일 선택</button>
        <button type="button" className="secondary-action" disabled={busy} onClick={() => folderInput.current?.click()}>폴더 선택</button>
      </div>
      <input ref={input} id="image-upload-files" type="file" hidden multiple accept={kind === 'images' ? 'image/png,image/jpeg,image/gif,image/bmp,.png,.jpg,.jpeg,.gif,.bmp' : undefined}
        disabled={busy} aria-label="업로드할 파일 선택" aria-describedby="image-upload-help" onChange={event => {
          addFiles(Array.from(event.target.files ?? []))
          event.target.value = ''
        }} />
      <input type="file" hidden multiple disabled={busy}
      ref={element => { folderInput.current = element; element?.setAttribute('webkitdirectory', '') }}
      aria-label="업로드할 폴더 선택" onChange={event => {
        addFiles(Array.from(event.target.files ?? []))
        event.target.value = ''
      }} />
      <div className={`image-upload-dropzone${busy ? ' upload-disabled' : ''}${dragging ? ' upload-dragging' : ''}`}
        onDragEnter={event => { event.preventDefault(); if (!busy && event.dataTransfer.types.includes('Files')) { dropDepth.current++; setDragging(true) } }}
        onDragLeave={event => { event.preventDefault(); dropDepth.current = Math.max(0, dropDepth.current - 1); if (!dropDepth.current) setDragging(false) }}
        onDragOver={event => { event.preventDefault(); event.dataTransfer.dropEffect = busy ? 'none' : 'copy' }}
        onDrop={async event => {
          event.preventDefault()
          dropDepth.current = 0
          setDragging(false)
          if (submitting.current || readingDrop.current || finished) return
          readingDrop.current = true
          setReading(true)
          setNotice(null)
          try {
            const files = await droppedUploadFiles(event.dataTransfer)
            if (!mounted.current) return
            if (files.length) addFiles(files)
            else setNotice('추가할 파일이 없습니다. 빈 폴더는 업로드하지 않습니다.')
          } catch (reason: unknown) {
            if (mounted.current) setNotice(reason instanceof Error ? reason.message : '파일을 읽지 못했습니다. 폴더 선택 버튼을 이용해 주세요.')
          } finally {
            readingDrop.current = false
            if (mounted.current) setReading(false)
          }
        }}>
        <strong>{reading ? '폴더의 파일을 확인하고 있습니다…' : dragging ? '여기에 놓아 추가하세요' : '파일 또는 폴더를 여기에 놓으세요'}</strong>
        <span>하위 폴더의 파일과 원본 경로를 함께 가져옵니다.</span>
      </div>
    </>}
    <p role="status" aria-live="polite">{running ? `업로드 중 · 완료 ${successCount + failedCount} / ${items.length}개` : finished
      ? `성공 ${successCount} / 실패 ${failedCount} / 미전송 ${pendingCount}` : `${items.length}개 파일 선택`}</p>
    {running && <p className="field-description">파일을 순서대로 전송하고 있습니다. 완료될 때까지 이 창을 유지해 주세요.</p>}
    {notice && <p role="alert">{notice}</p>}
    {items.length > 0 && <ul className="image-upload-files" aria-label="파일별 업로드 상태">{items.map(item => <li key={item.id}>
      <div><button type="button" className="dataset-name-button" aria-expanded={Boolean(item.descriptionOpen)}
        aria-controls={`image-upload-description-${item.id}`} onClick={() => setItems(current => current.map(target =>
          target.id === item.id ? { ...target, descriptionOpen: !target.descriptionOpen } : target))}><strong>{item.file.webkitRelativePath || item.file.name}</strong></button>
        <small>{new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 1 }).format(item.file.size / 1024)} KB</small></div>
      <span className={item.status === '실패' ? 'upload-failure' : ''}>{item.status}</span>
      {!running && !finished && <button type="button" className="secondary-action" aria-label={`${item.file.webkitRelativePath || item.file.name} 선택 제거`}
        onClick={() => setItems(current => current.filter(target => target.id !== item.id))}>제거</button>}
      <label id={`image-upload-description-${item.id}`} className="upload-file-description" hidden={!item.descriptionOpen}>설명 <small>선택 · 최대 2,000자</small>
        <textarea rows={2} maxLength={2000} value={item.description} disabled={running || finished}
          aria-label={`${item.file.webkitRelativePath || item.file.name} 설명`} onChange={event => {
            const description = event.target.value
            setItems(current => current.map(target => target.id === item.id ? { ...target, description } : target))
          }} /></label>
      {item.message && <p className="upload-failure">{item.message}</p>}
    </li>)}</ul>}
    {finished && <p className="field-description">닫은 뒤 목록에서 등록 결과를 확인해 주세요. 다시 업로드하려면 파일을 재선택해 주세요.</p>}
    <div className="form-actions">{!finished && <button className="primary-action" type="button"
      disabled={busy || items.length === 0} onClick={startUpload}>{running ? '업로드 중…' : '업로드 시작'}</button>}</div>
  </dialog>
}
