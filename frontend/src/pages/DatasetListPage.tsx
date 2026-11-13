import { useEffect, useLayoutEffect, useRef, useState, type PointerEvent } from 'react'
import { deleteDataset, fetchDatasets, type DatasetListItem } from '../api/datasets'
import DatasetDetailModal from './DatasetDetailModal'

const dateTime = new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })
const columnLabels = ['선택', '이름', '설명', '워크스페이스', '이미지 개수', '생성자 ID', '생성자 이름', '생성일시', '수정일시']

function DatasetContextMenu({ target, onClose, onDetail }: {
  target: { dataset: DatasetListItem; x: number; y: number; trigger: HTMLElement }
  onClose: () => void
  onDetail: () => void
}) {
  const menu = useRef<HTMLDivElement>(null)
  useLayoutEffect(() => {
    const element = menu.current!
    const bounds = element.getBoundingClientRect()
    element.style.left = `${Math.max(8, Math.min(target.x, window.innerWidth - bounds.width - 8))}px`
    element.style.top = `${Math.max(8, Math.min(target.y, window.innerHeight - bounds.height - 8))}px`
    element.querySelector<HTMLElement>('[role="menuitem"]')?.focus()
  }, [target])
  useEffect(() => {
    function outside(event: globalThis.PointerEvent) {
      if (!menu.current?.contains(event.target as Node)) onClose()
    }
    function dismiss() { onClose() }
    document.addEventListener('pointerdown', outside)
    window.addEventListener('resize', dismiss)
    window.addEventListener('scroll', dismiss, true)
    return () => {
      document.removeEventListener('pointerdown', outside)
      window.removeEventListener('resize', dismiss)
      window.removeEventListener('scroll', dismiss, true)
    }
  }, [onClose])
  return <div ref={menu} className="dataset-context-menu" role="menu" aria-label={`${target.dataset.name} · ${target.dataset.workspaceName} 메뉴`}
    style={{ left: target.x, top: target.y }} onContextMenu={event => event.preventDefault()} onKeyDown={event => {
      if (event.key === 'Escape') { event.preventDefault(); onClose(); target.trigger.focus() }
      if (event.key === 'Tab') onClose()
      if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
        event.preventDefault()
        const items = Array.from(menu.current!.querySelectorAll<HTMLElement>('[role="menuitem"]'))
        const index = items.indexOf(document.activeElement as HTMLElement)
        items[event.key === 'Home' ? 0 : event.key === 'End' ? items.length - 1 : (index + (event.key === 'ArrowUp' ? -1 : 1) + items.length) % items.length]?.focus()
      }
    }}>
    <div className="dataset-context-info" role="presentation"><strong>{target.dataset.name}</strong>
      <span>{target.dataset.workspaceName}</span><small>{target.dataset.datasetId}</small></div>
    <a role="menuitem" href={`/datasets/manage?datasetId=${encodeURIComponent(target.dataset.datasetId)}`}>데이터셋 관리</a>
    <button role="menuitem" type="button" onClick={onDetail}>상세보기</button>
  </div>
}

export default function DatasetListPage({ accessToken }: { accessToken: string }) {
  const [datasets, setDatasets] = useState<DatasetListItem[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  const [selectedIds, setSelectedIds] = useState<string[]>([])
  const [deleting, setDeleting] = useState(false)
  const [detailId, setDetailId] = useState<string | null>(null)
  const [contextTarget, setContextTarget] = useState<{ dataset: DatasetListItem; x: number; y: number; trigger: HTMLElement } | null>(null)
  const [deleteResult, setDeleteResult] = useState<{ deleted: number; failures: string[] } | null>(null)
  const selectAll = useRef<HTMLInputElement>(null)
  const submitting = useRef(false)
  const [columnWidths, setColumnWidths] = useState(() => {
    const widths = [52, 150, 150, 150, 100, 150, 150, 149, 149]
    const total = widths.reduce((sum, width) => sum + width, 0)
    return widths.map(width => width / total * 100)
  })
  const resizing = useRef<{ index: number; startX: number; widths: number[] } | null>(null)

  function resizeColumns(index: number, delta: number, widths: number[]) {
    const total = widths.reduce((sum, width) => sum + width, 0)
    const minimum = Math.min(index === 0 ? 44 : 80, widths[index])
    const nextMinimum = Math.min(80, widths[index + 1])
    const change = Math.max(minimum - widths[index], Math.min(delta, widths[index + 1] - nextMinimum))
    setColumnWidths(widths.map((width, position) =>
      (width + (position === index ? change : position === index + 1 ? -change : 0)) / total * 100))
  }

  function startResize(event: PointerEvent<HTMLSpanElement>, index: number) {
    if (event.button !== 0) return
    event.preventDefault()
    const widths = Array.from(event.currentTarget.closest('tr')!.querySelectorAll('th'), header => header.getBoundingClientRect().width)
    resizing.current = { index, startX: event.clientX, widths }
    event.currentTarget.setPointerCapture(event.pointerId)
  }

  function resizeHandle(index: number) {
    return <span className="column-resize-handle" role="separator" aria-orientation="vertical" tabIndex={0}
      aria-label={`${columnLabels[index]} 컬럼 너비 조절`}
      onPointerDown={event => startResize(event, index)}
      onPointerMove={event => {
        const drag = resizing.current
        if (drag?.index === index) resizeColumns(index, event.clientX - drag.startX, drag.widths)
      }}
      onPointerUp={event => {
        resizing.current = null
        if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId)
      }}
      onLostPointerCapture={() => { resizing.current = null }}
      onKeyDown={event => {
        if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
        event.preventDefault()
        const widths = Array.from(event.currentTarget.closest('tr')!.querySelectorAll('th'), header => header.getBoundingClientRect().width)
        resizeColumns(index, event.key === 'ArrowRight' ? 16 : -16, widths)
      }} />
  }

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
    setDetailId(null)
    setContextTarget(null)
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
          <colgroup>{columnWidths.map((width, index) => <col key={columnLabels[index]} style={{ width: `${width}%` }} />)}</colgroup>
          <thead><tr><th scope="col" className="selection-column"><input ref={selectAll} type="checkbox"
            aria-label="데이터셋 전체 선택" disabled={deleting}
            checked={datasets.length > 0 && selectedIds.length === datasets.length}
            onChange={event => setSelectedIds(event.target.checked ? datasets.map(dataset => dataset.datasetId) : [])} />{resizeHandle(0)}</th>
            {columnLabels.slice(1).map((label, index) => <th key={label} scope="col">{label}
              {index + 1 < columnLabels.length - 1 && resizeHandle(index + 1)}</th>)}</tr></thead>
          <tbody>{datasets.map(dataset => <tr key={dataset.datasetId} tabIndex={0}
            onContextMenu={event => {
              event.preventDefault()
              if (!deleting) setContextTarget({ dataset, x: event.clientX, y: event.clientY, trigger: event.currentTarget })
            }} onKeyDown={event => {
              if (event.key !== 'ContextMenu' && !(event.shiftKey && event.key === 'F10')) return
              event.preventDefault()
              const bounds = event.currentTarget.getBoundingClientRect()
              if (!deleting) setContextTarget({ dataset, x: bounds.left + 24, y: bounds.top + 24, trigger: event.currentTarget })
            }}>
            <td className="selection-column"><input type="checkbox" disabled={deleting}
              aria-label={`${dataset.name} · ${dataset.workspaceName} 선택`}
              checked={selectedIds.includes(dataset.datasetId)} onChange={event => {
                const checked = event.target.checked
                setSelectedIds(current => checked ? [...current, dataset.datasetId] : current.filter(id => id !== dataset.datasetId))
              }} /></td>
            <th scope="row"><button type="button" className="dataset-name-button" disabled={deleting}
              aria-haspopup="dialog" aria-label={`${dataset.name} 상세 보기`}
              onClick={() => setDetailId(dataset.datasetId)}>{dataset.name}</button></th><td>{dataset.description ?? '설명 없음'}</td>
            <td title={dataset.workspaceId}>{dataset.workspaceName}</td>
            <td>{new Intl.NumberFormat('ko-KR').format(dataset.imageCount)}개</td>
            <td>{dataset.creatorUserId ?? '-'}</td><td>{dataset.creatorUsername ?? '-'}</td>
            <td><time dateTime={dataset.createdAt}>{dateTime.format(new Date(dataset.createdAt))}</time></td>
            <td><time dateTime={dataset.updatedAt}>{dateTime.format(new Date(dataset.updatedAt))}</time></td>
          </tr>)}</tbody>
        </table>
      </div>}
    {contextTarget && <DatasetContextMenu target={contextTarget} onClose={() => setContextTarget(null)}
      onDetail={() => {
        contextTarget.trigger.focus()
        setDetailId(contextTarget.dataset.datasetId)
        setContextTarget(null)
      }} />}
    {detailId !== null && <DatasetDetailModal key={detailId} datasetId={detailId} accessToken={accessToken}
      onClose={() => setDetailId(null)} />}
  </section>
}
