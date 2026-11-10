import { useRef, useState, type SubmitEvent } from 'react'
import { createDataset, type Dataset } from '../api/datasets'

export default function DatasetCreatePage({ accessToken }: { accessToken: string }) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [created, setCreated] = useState<Dataset | null>(null)
  const submitting = useRef(false)

  async function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting.current || !name.trim()) return
    submitting.current = true
    setPending(true)
    setError(null)
    setCreated(null)
    try {
      const result = await createDataset({ name: name.trim(), description: description.trim() || null }, accessToken)
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
          {created && <p>“{created.name}” 데이터셋을 생성했습니다. 생성 ID: {created.datasetId}</p>}
        </div>
        <div className="form-actions">
          <a className="secondary-action" href="/">취소</a>
          <button className="primary-action" type="submit" disabled={pending || !name.trim()}>
            {pending ? '생성 중…' : '데이터셋 생성'}
          </button>
        </div>
      </form>
    </section>
  )
}
