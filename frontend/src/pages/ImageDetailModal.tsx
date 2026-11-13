import { useEffect, useRef, useState } from 'react'
import { fetchImageContent, type DatasetImage } from '../api/images'

export default function ImageDetailModal({ image, accessToken, onClose }: {
  image: DatasetImage; accessToken: string; onClose: () => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const [url, setUrl] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    if (dialog.current && !dialog.current.open) dialog.current.showModal()
  }, [])
  useEffect(() => {
    const controller = new AbortController()
    let objectUrl: string | undefined
    setUrl(null)
    setError(null)
    fetchImageContent(image.datasetId, image.imageId, accessToken, controller.signal).then(blob => {
      if (controller.signal.aborted) return
      objectUrl = URL.createObjectURL(blob)
      setUrl(objectUrl)
    }).catch((reason: unknown) => {
      if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : '원본 이미지를 불러오지 못했습니다. 연결을 확인해 주세요.')
    })
    return () => { controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl) }
  }, [image.datasetId, image.imageId, accessToken, attempt])
  return <dialog ref={dialog} className="dataset-detail-modal image-detail-modal" aria-labelledby="image-detail-title" onClose={onClose}
    onClick={event => {
      if (event.target !== event.currentTarget) return
      const bounds = event.currentTarget.getBoundingClientRect()
      if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) dialog.current?.close()
    }}>
    <div className="dataset-detail-heading"><h2 id="image-detail-title">이미지 상세</h2>
      <button className="secondary-action" type="button" onClick={() => dialog.current?.close()}>닫기</button></div>
    <p className="image-detail-filename">{image.originalFilename}</p>
    <p className="field-description">원본 이미지 · 버전 {image.versionCount} · {image.mediaType} · {new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 1 }).format(image.sizeBytes / 1024)} KB</p>
    {error ? <div role="alert"><p>{error}</p><button type="button" className="secondary-action" onClick={() => setAttempt(value => value + 1)}>다시 시도</button></div>
      : url ? <div className="image-detail-canvas"><img src={url} alt={image.originalFilename} onError={() => setError('이 이미지를 화면에 표시하지 못했습니다.')} /></div>
      : <p role="status">원본 이미지를 불러오고 있습니다.</p>}
  </dialog>
}
