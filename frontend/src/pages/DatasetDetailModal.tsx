import { useEffect, useRef, useState } from 'react'
import { fetchDatasetDetail, type DatasetListItem } from '../api/datasets'

const dateTime = new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })

export default function DatasetDetailModal({ datasetId, accessToken, onClose }: {
  datasetId: string; accessToken: string; onClose: () => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const [dataset, setDataset] = useState<DatasetListItem | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (dialog.current && !dialog.current.open) dialog.current.showModal()
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    setDataset(null)
    setError(null)
    fetchDatasetDetail(datasetId, accessToken, controller.signal).then(result => {
      if (!controller.signal.aborted) setDataset(result)
    }).catch((reason: unknown) => {
      if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : '상세를 불러오지 못했습니다.')
    })
    return () => controller.abort()
  }, [datasetId, accessToken, attempt])

  return <dialog ref={dialog} className="dataset-detail-modal" aria-labelledby="dataset-detail-title" onClose={onClose}>
    <div className="dataset-detail-heading">
      <h2 id="dataset-detail-title">데이터셋 상세</h2>
      <button type="button" className="secondary-action" onClick={() => dialog.current?.close()}>닫기</button>
    </div>
    {error ? <div role="alert">
      <p>{error}</p>
      <button type="button" className="secondary-action" onClick={() => setAttempt(value => value + 1)}>다시 시도</button>
    </div> : dataset === null ? <p role="status">데이터셋 상세를 불러오고 있습니다.</p>
      : <dl className="dataset-detail-fields">
        <dt>이름</dt><dd>{dataset.name}</dd>
        <dt>데이터셋 ID</dt><dd>{dataset.datasetId}</dd>
        <dt>설명</dt><dd>{dataset.description ?? '설명 없음'}</dd>
        <dt>워크스페이스</dt><dd>{dataset.workspaceName}</dd>
        <dt>워크스페이스 ID</dt><dd>{dataset.workspaceId}</dd>
        <dt>생성자 ID</dt><dd>{dataset.creatorUserId ?? '-'}</dd>
        <dt>생성자 이름</dt><dd>{dataset.creatorUsername ?? '-'}</dd>
        <dt>생성일시</dt><dd><time dateTime={dataset.createdAt}>{dateTime.format(new Date(dataset.createdAt))}</time></dd>
        <dt>수정일시</dt><dd><time dateTime={dataset.updatedAt}>{dateTime.format(new Date(dataset.updatedAt))}</time></dd>
      </dl>}
    {dataset && !error && <div className="form-actions">
      <a className="primary-action" href={`/datasets/manage?datasetId=${encodeURIComponent(dataset.datasetId)}`}>데이터셋 관리</a>
    </div>}
  </dialog>
}
