import { useEffect, useRef, useState } from 'react'
import { deleteDataset, fetchDatasets, type DatasetListItem } from '../api/datasets'

const dateTime = new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })

export default function DatasetListPage({ accessToken }: { accessToken: string }) {
  const [datasets, setDatasets] = useState<DatasetListItem[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  const [selectedIds, setSelectedIds] = useState<string[]>([])
  const [deleting, setDeleting] = useState(false)
  const [deleteResult, setDeleteResult] = useState<{ deleted: number; failures: string[] } | null>(null)
  const selectAll = useRef<HTMLInputElement>(null)
  const submitting = useRef(false)

  useEffect(() => {
    if (selectAll.current) {
      selectAll.current.indeterminate = selectedIds.length > 0 && selectedIds.length < (datasets?.length ?? 0)
    }
  }, [selectedIds, datasets])

  useEffect(() => {
    const controller = new AbortController()
    setDatasets(null)
    setError(null)
    setSelectedIds([])
    setDeleteResult(null)
    fetchDatasets(accessToken, controller.signal).then(result => {
      if (!controller.signal.aborted) setDatasets(result)
    }).catch((reason: unknown) => {
      if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : '목록을 불러오지 못했습니다.')
    })
    return () => controller.abort()
  }, [accessToken, attempt])

  async function handleDelete() {
    if (submitting.current || !datasets || selectedIds.length === 0) return
    const targets = datasets.filter(dataset => selectedIds.includes(dataset.datasetId))
    if (!window.confirm(`선택한 ${targets.length}개 데이터셋을 삭제하시겠습니까? 삭제 후에는 되돌릴 수 없습니다.`)) return
    submitting.current = true
    setDeleting(true)
    setDeleteResult(null)
    const deletedIds: string[] = []
    const failedIds: string[] = []
    const failures: string[] = []
    try {
      for (const dataset of targets) {
        try {
          await deleteDataset(dataset.datasetId, accessToken)
          deletedIds.push(dataset.datasetId)
        } catch (reason: unknown) {
          failedIds.push(dataset.datasetId)
          failures.push(`${dataset.name} (${dataset.workspaceName}): ${reason instanceof Error ? reason.message : '삭제하지 못했습니다.'}`)
        }
      }
      setDatasets(current => current?.filter(dataset => !deletedIds.includes(dataset.datasetId)) ?? null)
      setSelectedIds(failedIds)
      setDeleteResult({ deleted: deletedIds.length, failures })
    } finally {
      submitting.current = false
      setDeleting(false)
    }
  }

  return <section className="dataset-list" aria-labelledby="dataset-list-title">
    <p className="page-location">데이터셋 / 데이터셋 목록</p>
    <div className="dataset-list-heading">
      <h1 id="dataset-list-title">데이터셋 목록</h1>
      <a className="primary-action" href="/datasets/new">데이터셋 생성</a>
    </div>
    <p className="intro">내가 가입 승인된 활성 워크스페이스의 데이터셋을 모아 보여줍니다.</p>
    <div className="dataset-list-actions">
      <span aria-live="polite">{selectedIds.length}개 선택</span>
      <button className="danger-action" type="button" disabled={deleting || selectedIds.length === 0}
        onClick={handleDelete}>{deleting ? '삭제 중…' : '선택 삭제'}</button>
    </div>
    {deleteResult && <div className="dataset-delete-result" role={deleteResult.failures.length ? 'alert' : 'status'}>
      <p>{deleteResult.deleted}개 삭제 완료{deleteResult.failures.length > 0 && ` · ${deleteResult.failures.length}개 삭제 실패`}</p>
      {deleteResult.failures.length > 0 && <ul>{deleteResult.failures.map((message, index) => <li key={index}>{message}</li>)}</ul>}
    </div>}
    {error ? <div className="dataset-list-state" role="alert">
      <p>{error}</p><button className="secondary-action" onClick={() => setAttempt(value => value + 1)}>다시 시도</button>
    </div> : datasets === null ? <p role="status">데이터셋을 불러오고 있습니다.</p>
      : datasets.length === 0 ? <div className="dataset-list-state" role="status">
        <h2>조회할 데이터셋이 없습니다</h2>
        <p>가입 승인된 워크스페이스에 데이터셋을 생성하면 이곳에서 확인할 수 있습니다.</p>
      </div> : <div className="dataset-table-scroll" role="region" aria-label="데이터셋 목록 표" tabIndex={0}>
        <table className="dataset-table">
          <caption>데이터셋 {datasets.length}개 · 최신 생성순 · 일시는 현지 시간 기준</caption>
          <thead><tr><th scope="col" className="selection-column"><input ref={selectAll} type="checkbox"
            aria-label="데이터셋 전체 선택" disabled={deleting}
            checked={datasets.length > 0 && selectedIds.length === datasets.length}
            onChange={event => setSelectedIds(event.target.checked ? datasets.map(dataset => dataset.datasetId) : [])} /></th>
            <th scope="col">이름</th><th scope="col">설명</th><th scope="col">워크스페이스</th>
            <th scope="col">생성자 ID</th><th scope="col">생성자 이름</th>
            <th scope="col">생성일시</th><th scope="col">수정일시</th></tr></thead>
          <tbody>{datasets.map(dataset => <tr key={dataset.datasetId}>
            <td className="selection-column"><input type="checkbox" disabled={deleting}
              aria-label={`${dataset.name} · ${dataset.workspaceName} 선택`}
              checked={selectedIds.includes(dataset.datasetId)} onChange={event => {
                const checked = event.target.checked
                setSelectedIds(current => checked ? [...current, dataset.datasetId] : current.filter(id => id !== dataset.datasetId))
              }} /></td>
            <th scope="row">{dataset.name}</th><td>{dataset.description ?? '설명 없음'}</td>
            <td title={dataset.workspaceId}>{dataset.workspaceName}</td>
            <td>{dataset.creatorUserId ?? '-'}</td><td>{dataset.creatorUsername ?? '-'}</td>
            <td><time dateTime={dataset.createdAt}>{dateTime.format(new Date(dataset.createdAt))}</time></td>
            <td><time dateTime={dataset.updatedAt}>{dateTime.format(new Date(dataset.updatedAt))}</time></td>
          </tr>)}</tbody>
        </table>
      </div>}
  </section>
}
