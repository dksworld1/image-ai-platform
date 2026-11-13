import { useEffect, useRef, useState } from 'react'
import { ImageUploadError, uploadImage, type ImageDatasetTarget } from '../api/images'

type UploadItem = { id: number; file: File; status: '대기' | '업로드 중' | '성공' | '실패' | '미전송'; message?: string }

export default function ImageUploadModal({ dataset, accessToken, onClose, onComplete, replaceExisting = false }: {
  dataset: ImageDatasetTarget; accessToken: string; onClose: () => void; onComplete: () => void; replaceExisting?: boolean
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const input = useRef<HTMLInputElement>(null)
  const sequence = useRef(0)
  const request = useRef<AbortController | null>(null)
  const submitting = useRef(false)
  const [items, setItems] = useState<UploadItem[]>([])
  const [running, setRunning] = useState(false)
  const [finished, setFinished] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    if (dialog.current && !dialog.current.open) dialog.current.showModal()
    return () => request.current?.abort()
  }, [])

  function addFiles(files: File[]) {
    if (submitting.current || finished) return
    setItems(current => [...current, ...files.map(file => ({ id: sequence.current++, file, status: '대기' as const }))])
  }

  async function startUpload() {
    if (submitting.current || finished || items.length === 0) return
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
          await uploadImage(dataset.datasetId, item.file, accessToken, controller.signal, replaceExisting)
          if (controller.signal.aborted) return
          update(item.id, '성공')
        } catch (reason: unknown) {
          if (controller.signal.aborted) return
          update(item.id, '실패', reason instanceof Error ? reason.message : '등록 결과를 확인할 수 없습니다. 목록을 확인해 주세요.')
          if (reason instanceof ImageUploadError && (reason.status === 401 || reason.status === 403)) {
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
    onClose={onClose} onCancel={event => { if (submitting.current) event.preventDefault() }}>
    <div className="dataset-detail-heading"><h2 id="image-upload-title">{replaceExisting ? '이미지 수정 업로드' : '이미지 업로드'}</h2>
      <button type="button" className="secondary-action" disabled={running} onClick={() => dialog.current?.close()}>닫기</button></div>
    <p className="upload-target"><strong>{dataset.name}</strong><span>{dataset.workspaceName}</span><small>{dataset.datasetId}</small></p>
    {replaceExisting && <p className="image-replace-warning" role="alert"><strong>기존 파일을 덮어씁니다.</strong><br />
      이 데이터셋에 같은 원본 파일명이 있으면 새 버전으로 교체합니다. 기존 기록과 파일은 보관하며, 같은 이름이 없으면 새로 등록합니다.</p>}
    <p id="image-upload-help" className="field-description">PNG·JPEG·GIF·BMP 파일을 선택하세요. 기본 파일당 10MB이며 실제 제한은 서버 설정에 따릅니다. 썸네일은 자동 생성됩니다.</p>
    <p className="field-description">워크스페이스 운영자 또는 데이터셋 생성자에게 허용되며 {replaceExisting ? '이미지 수정 권한이 필요합니다. 새 이름의 파일을 등록하려면 업로드 권한도 필요합니다.' : '이미지 업로드 권한이 필요합니다.'} 최종 권한은 서버에서 확인합니다.</p>
    {!finished && <div className={`image-upload-dropzone${running ? ' upload-disabled' : ''}`}
      onDragOver={event => { event.preventDefault(); event.dataTransfer.dropEffect = running ? 'none' : 'copy' }}
      onDrop={event => { event.preventDefault(); addFiles(Array.from(event.dataTransfer.files)) }}>
      <input ref={input} id="image-upload-files" type="file" multiple accept="image/png,image/jpeg,image/gif,image/bmp,.png,.jpg,.jpeg,.gif,.bmp"
        disabled={running} aria-describedby="image-upload-help" onChange={event => {
          addFiles(Array.from(event.target.files ?? []))
          event.target.value = ''
        }} />
      <label htmlFor="image-upload-files">파일 선택 또는 여기에 파일 놓기</label>
    </div>}
    <p role="status" aria-live="polite">{running ? `업로드 중 · 완료 ${successCount + failedCount} / ${items.length}개` : finished
      ? `성공 ${successCount} / 실패 ${failedCount} / 미전송 ${pendingCount}` : `${items.length}개 파일 선택`}</p>
    {running && <p className="field-description">파일을 순서대로 전송하고 있습니다. 완료될 때까지 이 창을 유지해 주세요.</p>}
    {notice && <p role="alert">{notice}</p>}
    {items.length > 0 && <ul className="image-upload-files" aria-label="파일별 업로드 상태">{items.map(item => <li key={item.id}>
      <div><strong>{item.file.name}</strong><small>{new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 1 }).format(item.file.size / 1024)} KB</small></div>
      <span className={item.status === '실패' ? 'upload-failure' : ''}>{item.status}</span>
      {!running && !finished && <button type="button" className="secondary-action" aria-label={`${item.file.name} 선택 제거`}
        onClick={() => setItems(current => current.filter(target => target.id !== item.id))}>제거</button>}
      {item.message && <p className="upload-failure">{item.message}</p>}
    </li>)}</ul>}
    {finished && <p className="field-description">닫은 뒤 목록에서 등록 결과를 확인해 주세요. 다시 업로드하려면 파일을 재선택해 주세요.</p>}
    <div className="form-actions">{!finished && <button className="primary-action" type="button"
      disabled={running || items.length === 0} onClick={startUpload}>{running ? '업로드 중…' : '업로드 시작'}</button>}</div>
  </dialog>
}
