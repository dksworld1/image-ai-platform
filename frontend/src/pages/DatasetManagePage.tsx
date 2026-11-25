import { useEffect, useState } from 'react'
import { fetchVersionTargets } from '../api/datasetVersions'
import type { ImageDatasetTarget } from '../api/images'
import { fetchWorkspaces, type Workspace } from '../api/workspaces'
import DatasetVersionPanel from './DatasetVersionPanel'
import DatasetFileEditor from './DatasetFileEditor'
export { getEntryDescriptionChanges } from './DatasetFileEditor'

export default function DatasetManagePage({ accessToken }: { accessToken: string }) {
  const [initialId] = useState(() => new URLSearchParams(window.location.search).get('datasetId') ?? '')
  const [datasets, setDatasets] = useState<ImageDatasetTarget[] | null>(null)
  const [workspaces, setWorkspaces] = useState<Workspace[]>([])
  const [workspaceId, setWorkspaceId] = useState('')
  const [datasetId, setDatasetId] = useState(initialId)
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  const [refreshKey, setRefreshKey] = useState(0)
  const [busy, setBusy] = useState(false)
  const selected = datasets?.find(item => item.datasetId === datasetId)
  useEffect(() => {
    const syncDataset = () => {
      const id = new URLSearchParams(location.search).get('datasetId')
      const target = datasets?.find(item => item.datasetId === id)
      setDatasetId(target?.datasetId ?? '')
      setWorkspaceId(target?.workspaceId ?? '')
    }
    window.addEventListener('popstate', syncDataset)
    return () => window.removeEventListener('popstate', syncDataset)
  }, [datasets])
  useEffect(() => {
    const controller = new AbortController()
    setDatasets(null); setError(null)
    Promise.all([fetchVersionTargets(accessToken, controller.signal), fetchWorkspaces(accessToken, controller.signal)]).then(([items, spaces]) => {
      if (controller.signal.aborted) return
      setDatasets(items); setWorkspaces(spaces)
      const requestedId = new URLSearchParams(window.location.search).get('datasetId') ?? initialId
      const target = items.find(item => item.datasetId === requestedId)
      setDatasetId(target?.datasetId ?? ''); setWorkspaceId(target?.workspaceId ?? '')
      if (requestedId && !target) setError('선택한 데이터셋에 접근할 수 없습니다. 다른 데이터셋을 선택해 주세요.')
    }).catch((reason: unknown) => { if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : '선택 목록을 불러오지 못했습니다.') })
    return () => controller.abort()
  }, [accessToken, attempt, initialId])
  function choose(id: string) {
    setDatasetId(id); setError(null)
    window.history.replaceState(null, '', `/datasets/manage${id ? `?datasetId=${encodeURIComponent(id)}` : ''}`)
  }
  return <section className="dataset-list" aria-labelledby="dataset-version-page-title">
    <p className="page-location">데이터셋 / 데이터셋 관리</p>
    <div className="dataset-list-heading"><h1 id="dataset-version-page-title">데이터셋 관리</h1>
      <a className="secondary-action" href="/datasets" aria-disabled={busy} onClick={event => { if (busy) event.preventDefault() }}>데이터셋 목록으로</a>
    </div>
    <p className="intro">{selected ? `${selected.workspaceName} / ${selected.name}` : '워크스페이스와 데이터셋을 선택해 버전을 관리하세요.'}</p>
    <div className="image-list-filters">
      <label>워크스페이스<select value={workspaceId} disabled={datasets === null || busy} onChange={event => { setWorkspaceId(event.target.value); choose('') }}>
        <option value="">워크스페이스 선택</option>{workspaces.map(space => <option key={space.workspaceId} value={space.workspaceId}>{space.name} · {space.workspaceId.slice(-8)}</option>)}
      </select></label>
      <label>데이터셋<select value={datasetId} disabled={!workspaceId || datasets === null || busy} onChange={event => choose(event.target.value)}>
        <option value="">데이터셋 선택</option>{datasets?.filter(item => item.workspaceId === workspaceId).map(item => <option key={item.datasetId} value={item.datasetId}>{item.name} · {item.datasetId.slice(-8)}</option>)}
      </select></label>
      <button className="secondary-action" disabled={!selected || busy} onClick={() => setRefreshKey(value => value + 1)}>새로고침</button>
    </div>
    {error && <div role="alert"><p>{error}</p><button className="secondary-action" disabled={busy} onClick={() => setAttempt(value => value + 1)}>선택 목록 다시 불러오기</button></div>}
    {datasets === null ? !error && <p role="status">선택 목록을 불러오고 있습니다.</p>
      : selected ? <DatasetVersionPanel key={selected.datasetId} datasetId={selected.datasetId} accessToken={accessToken} refreshKey={refreshKey} onBusyChange={setBusy}
        renderEditor={(workingDatasetId, onEditorBusy, onEditorDirty) => <DatasetFileEditor key={workingDatasetId}
          target={{ ...selected, datasetId: workingDatasetId }} accessToken={accessToken} onBusyChange={onEditorBusy} onDirtyChange={onEditorDirty} />} />
      : <p role="status">{workspaces.length === 0 ? '가입한 워크스페이스가 없습니다.' : '버전을 관리할 데이터셋을 선택해 주세요.'}</p>}
  </section>
}
