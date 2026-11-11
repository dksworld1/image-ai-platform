import { useEffect, useRef, useState, type SubmitEvent } from 'react'
import { createDataset, type Dataset } from '../api/datasets'
import { fetchWorkspaces, type Workspace } from '../api/workspaces'

export default function DatasetCreatePage({ accessToken }: { accessToken: string }) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [created, setCreated] = useState<Dataset[] | null>(null)
  const submitting = useRef(false)
  const [workspaces, setWorkspaces] = useState<Workspace[] | null>(null)
  const [workspaceIds, setWorkspaceIds] = useState<string[]>([])
  const [workspaceError, setWorkspaceError] = useState<string | null>(null)
  const [workspaceAttempt, setWorkspaceAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setWorkspaces(null)
    setWorkspaceError(null)
    fetchWorkspaces(accessToken, controller.signal).then(result => {
      if (controller.signal.aborted) return
      setWorkspaces(result)
      setWorkspaceIds(current => current.filter(id => result.some(space => space.workspaceId === id)))
    }).catch((reason: unknown) => {
      if (!controller.signal.aborted) setWorkspaceError(reason instanceof Error ? reason.message : '워크스페이스 조회 실패')
    })
    return () => controller.abort()
  }, [accessToken, workspaceAttempt])

  async function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting.current || !name.trim() || !workspaces || workspaceIds.length === 0) return
    submitting.current = true
    setPending(true)
    setError(null)
    setCreated(null)
    try {
      const result = await createDataset({ name: name.trim(), description: description.trim() || null, workspaceIds }, accessToken)
      setCreated(result)
      setName('')
      setDescription('')
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : '데이터셋을 생성하지 못했습니다.')
    } finally {
      submitting.current = false
      setPending(false)
    }
  }

  return (
    <section className="dataset-create" aria-labelledby="dataset-create-title">
      <p className="page-location">데이터셋 / 데이터셋 생성</p>
      <h1 id="dataset-create-title">데이터셋 생성</h1>
      <p className="intro">이미지를 모아 관리할 데이터셋의 기본 정보를 입력하세요.</p>

      <form className="dataset-form" onSubmit={handleSubmit} aria-busy={pending}>
        <fieldset className="dataset-fields" disabled={pending}>
          <legend>기본 정보</legend>
          <div className="form-field">
            <p id="workspace-select-label" className="field-label">워크스페이스 (필수)</p>
            <p className="field-description" id="workspace-select-help">선택한 공간마다 독립 데이터셋이 생성됩니다. 이후 수정·삭제는 각 공간에만 적용됩니다.</p>
            {workspaceError ? <p role="alert" className="login-error">{workspaceError}</p>
              : workspaces === null ? <p role="status">워크스페이스를 불러오고 있습니다.</p>
                : workspaces.length === 0 ? <p role="status">가입 승인된 활성 워크스페이스가 없어 생성할 수 없습니다.</p>
                  : <details className="workspace-select" onKeyDown={event => {
                    if (event.key === 'Escape') {
                      event.currentTarget.open = false
                      event.currentTarget.querySelector('summary')?.focus()
                    }
                  }}>
                    <summary aria-describedby="workspace-select-help">워크스페이스 선택 · {workspaceIds.length}개 선택</summary>
                    <div className="workspace-options" role="group" aria-labelledby="workspace-select-label">
                      {workspaces.map(space => <label key={space.workspaceId}>
                        <input type="checkbox" name="workspaceIds" value={space.workspaceId}
                          checked={workspaceIds.includes(space.workspaceId)}
                          onChange={event => setWorkspaceIds(current => event.target.checked
                            ? [...current, space.workspaceId] : current.filter(id => id !== space.workspaceId))} />
                        <span>{space.name}<small>{space.workspaceId}</small></span>
                      </label>)}
                    </div>
                  </details>}
            <button className="workspace-refresh secondary-action" type="button" disabled={pending}
              onClick={() => setWorkspaceAttempt(value => value + 1)}>워크스페이스 다시 불러오기</button>
          </div>
          <div className="form-field">
            <label htmlFor="dataset-name">이름 (필수)</label>
            <p className="field-description" id="dataset-name-help">데이터셋을 구분할 수 있는 이름을 입력하세요.</p>
            <input id="dataset-name" name="name" type="text" required maxLength={200} aria-describedby="dataset-name-help"
              value={name} onChange={event => setName(event.target.value)} />
          </div>
          <div className="form-field">
            <label htmlFor="dataset-description">설명</label>
            <p className="field-description" id="dataset-description-help">데이터의 내용이나 사용 목적을 적어 주세요.</p>
            <textarea id="dataset-description" name="description" rows={6} maxLength={2000} aria-describedby="dataset-description-help"
              value={description} onChange={event => setDescription(event.target.value)} />
          </div>
        </fieldset>

        {error && <p className="login-error" role="alert">{error}</p>}
        <div role="status" aria-live="polite">
          {pending && <p>데이터셋을 생성하고 있습니다.</p>}
          {created && <p>“{created[0].name}” 독립 데이터셋 {created.length}개를 생성했습니다. <a href="/datasets">목록에서 확인</a></p>}
        </div>
        <div className="form-actions">
          <a className="secondary-action" href="/datasets">취소</a>
          <button className="primary-action" type="submit" disabled={pending || !name.trim() || !workspaces || workspaceIds.length === 0}>
            {pending ? '생성 중…' : '데이터셋 생성'}
          </button>
        </div>
      </form>
    </section>
  )
}
