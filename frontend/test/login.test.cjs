const assert = require('node:assert/strict')
const fs = require('node:fs')
const { test } = require('node:test')
const ts = require('typescript')
const React = require('react')
const { renderToStaticMarkup } = require('react-dom/server')

// 추가 테스트 의존성 없이 실제 TypeScript 모듈을 Node에서 실행합니다.
for (const extension of ['.ts', '.tsx']) {
  require.extensions[extension] = (module, filename) => {
    module._compile(ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
      compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
    }).outputText, filename)
  }
}
const { login } = require('../src/api/auth.ts')
const { createDataset, fetchDatasetDetail } = require('../src/api/datasets.ts')
const App = require('../src/App.tsx').default
const { saveAuthSession, loadAuthSession, clearAuthSession } = require('../src/authSession.ts')

test('상세 조회는 선택 ID를 인코딩하고 현재 토큰과 취소 신호로 서버 정보를 조회한다', async t => {
  const dataset = { datasetId: 'dataset/a', name: '서버 상세' }
  const request = t.mock.method(globalThis, 'fetch', async () => Response.json(dataset))
  const controller = new AbortController()
  assert.deepEqual(await fetchDatasetDetail('dataset/a', 'token', controller.signal), dataset)
  const [url, options] = request.mock.calls[0].arguments
  assert.equal(url, '/api/datasets/dataset%2Fa')
  assert.equal(options.headers.Authorization, 'Bearer token')
  assert.equal(options.signal, controller.signal)
})

test('상세 접근 거부는 멤버십 안내를 표시하고 요청 취소는 그대로 전달한다', async t => {
  const request = t.mock.method(globalThis, 'fetch', async () => new Response('private server details', { status: 403 }))
  await assert.rejects(fetchDatasetDetail('dataset', 'token'), error => {
    assert.match(error.message, /권한.*멤버/)
    assert.doesNotMatch(error.message, /private server/)
    return true
  })
  const controller = new AbortController()
  controller.abort()
  request.mock.mockImplementation(async () => { throw controller.signal.reason })
  await assert.rejects(fetchDatasetDetail('dataset', 'token', controller.signal), error => error === controller.signal.reason)
})

function sessionStorageForTest(t) {
  const entries = new Map()
  const previousWindow = globalThis.window
  globalThis.window = { sessionStorage: {
    getItem: key => entries.get(key) ?? null,
    setItem: (key, value) => entries.set(key, value),
    removeItem: key => entries.delete(key),
  } }
  t.after(() => {
    if (previousWindow === undefined) delete globalThis.window
    else globalThis.window = previousWindow
  })
  return entries
}

test('인덱스 첫 화면은 로그인 폼이며 업무 메뉴를 표시하지 않는다', () => {
  const html = renderToStaticMarkup(React.createElement(App))
  assert.match(html, /<h1[^>]*>로그인<\/h1>/)
  assert.match(html, /autoComplete="username"/)
  assert.match(html, /type="password"/)
  assert.match(html, /autoComplete="current-password"/)
  assert.match(html, /<button[^>]*disabled=""/)
  assert.doesNotMatch(html, /<nav|프로젝트 시작 화면/)
})

test('로그인은 JSON 자격 증명을 보내고 토큰 응답을 반환한다', async t => {
  const token = { accessToken: 'test-token', userKey: 'admin-key', userId: 'admin', username: 'Test User', tokenType: 'Bearer', expiresIn: 900 }
  const request = t.mock.method(globalThis, 'fetch', async () => Response.json(token))
  assert.deepEqual(await login('tester', ' secret '), token)
  const [url, options] = request.mock.calls[0].arguments
  assert.equal(url, '/api/auth/token')
  assert.equal(options.method, 'POST')
  assert.deepEqual(options.headers, { 'Content-Type': 'application/json' })
  assert.deepEqual(JSON.parse(options.body), { userId: 'tester', password: ' secret ' })
})

test('401은 서버 오류 본문을 노출하지 않고 동일한 자격 증명 안내를 반환한다', async t => {
  t.mock.method(globalThis, 'fetch', async () => new Response('internal details', { status: 401 }))
  await assert.rejects(login('tester', 'wrong'), { message: '아이디 또는 비밀번호를 확인해 주세요.' })
})

test('서버 장애는 일반 실패 안내를 반환한다', async t => {
  t.mock.method(globalThis, 'fetch', async () => new Response('internal details', { status: 500 }))
  await assert.rejects(login('tester', 'password'), { message: '로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.' })
})

test('네트워크 장애는 연결 실패 안내를 반환한다', async t => {
  t.mock.method(globalThis, 'fetch', async () => { throw new TypeError('fetch failed') })
  await assert.rejects(login('tester', 'password'), { message: '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.' })
})

test('저장한 토큰을 새 앱 진입에서 복원하고 로그아웃 버튼을 표시한다', t => {
  const entries = sessionStorageForTest(t)
  t.mock.method(Date, 'now', () => 1000)
  const session = saveAuthSession({ accessToken: 'saved-token', userKey: 'admin-key', userId: 'admin', username: 'Test User', tokenType: 'Bearer', expiresIn: 900 })
  assert.equal(session.userId, 'admin')
  assert.equal(session.username, 'Test User')
  assert.equal(session.expiresAt, 901000)
  assert.deepEqual(loadAuthSession(), session)
  assert.equal(entries.size, 1)
  const html = renderToStaticMarkup(React.createElement(App))
  assert.match(html, /프로젝트 시작 화면/)
  assert.match(html, /로그인 계정/)
  assert.match(html, />admin</)
  assert.match(html, />Test User</)
  assert.match(html, /<nav[^>]*aria-label="업무 메뉴"/)
  assert.match(html, /href="\/datasets\/new"/)
  assert.match(html, /<button[^>]*>로그아웃<\/button>/)
  assert.doesNotMatch(html, /type="password"/)
})

test('로그아웃으로 저장 토큰을 제거하면 다시 로그인 화면을 표시한다', t => {
  const entries = sessionStorageForTest(t)
  saveAuthSession({ accessToken: 'saved-token', userKey: 'admin-key', userId: 'admin', username: 'Test User', tokenType: 'Bearer', expiresIn: 900 })
  clearAuthSession()
  assert.equal(entries.size, 0)
  assert.equal(loadAuthSession(), null)
  const html = renderToStaticMarkup(React.createElement(App))
  assert.match(html, /type="password"/)
  assert.doesNotMatch(html, /프로젝트 시작 화면/)
})

test('토큰 만료 시 저장값을 제거하고 로그인 화면을 표시한다', t => {
  const entries = sessionStorageForTest(t)
  const clock = t.mock.method(Date, 'now', () => 1000)
  saveAuthSession({ accessToken: 'saved-token', userKey: 'admin-key', userId: 'admin', username: 'Test User', tokenType: 'Bearer', expiresIn: 1 })
  clock.mock.mockImplementation(() => 2000)
  assert.equal(loadAuthSession(), null)
  assert.equal(entries.size, 0)
  assert.match(renderToStaticMarkup(React.createElement(App)), /type="password"/)
})

test('손상되거나 형식이 잘못된 저장값을 제거한다', t => {
  const entries = sessionStorageForTest(t)
  for (const value of ['not-json', 'null', '{}', '{"accessToken":123,"tokenType":"Bearer","expiresIn":900,"expiresAt":9999999999999}']) {
    entries.set('image-ai-platform.auth', value)
    assert.equal(loadAuthSession(), null)
    assert.equal(entries.size, 0)
  }
})

test('로그인 세션으로 생성 주소에 직접 접근하면 생성 페이지와 선택된 메뉴를 표시한다', t => {
  sessionStorageForTest(t)
  globalThis.window.location = { pathname: '/datasets/new' }
  saveAuthSession({ accessToken: 'saved-token', userKey: 'admin-key', userId: 'admin', username: 'Test User', tokenType: 'Bearer', expiresIn: 900 })
  const html = renderToStaticMarkup(React.createElement(App))
  assert.match(html, /href="\/datasets\/new" aria-current="page"/)
  assert.match(html, /<h1[^>]*>데이터셋 생성<\/h1>/)
  assert.match(html, /<label for="dataset-name">이름 \(필수\)<\/label>/)
  assert.match(html, /<label for="dataset-description">설명<\/label>/)
  assert.match(html, /<textarea[^>]*name="description"/)
  assert.match(html, /id="dataset-name"[^>]*required=""[^>]*maxLength="200"/)
  assert.match(html, /name="description"[^>]*maxLength="2000"/)
  assert.match(html, /<button[^>]*type="submit"[^>]*disabled=""[^>]*>데이터셋 생성<\/button>/)
  assert.match(html, /href="\/datasets">취소<\/a>/)
  assert.doesNotMatch(html, /프로젝트 시작 화면|role="dialog"/)
})

test('생성 주소도 비로그인이나 만료된 세션에는 로그인 화면만 표시한다', t => {
  sessionStorageForTest(t)
  globalThis.window.location = { pathname: '/datasets/new' }
  assert.match(renderToStaticMarkup(React.createElement(App)), /<h1[^>]*>로그인<\/h1>/)
  const clock = t.mock.method(Date, 'now', () => 1000)
  saveAuthSession({ accessToken: 'expired-token', userKey: 'admin-key', userId: 'admin', username: 'Test User', tokenType: 'Bearer', expiresIn: 1 })
  clock.mock.mockImplementation(() => 2000)
  const html = renderToStaticMarkup(React.createElement(App))
  assert.match(html, /<h1[^>]*>로그인<\/h1>/)
  assert.doesNotMatch(html, /dataset-name|<nav/)
})

test('알 수 없는 화면 주소는 생성 화면으로 취급하지 않는다', t => {
  sessionStorageForTest(t)
  globalThis.window.location = { pathname: '/missing' }
  saveAuthSession({ accessToken: 'saved-token', userKey: 'admin-key', userId: 'admin', username: 'Test User', tokenType: 'Bearer', expiresIn: 900 })
  const html = renderToStaticMarkup(React.createElement(App))
  assert.match(html, /페이지를 찾을 수 없습니다/)
  assert.match(html, /href="\/">시작 화면으로 이동/)
  assert.doesNotMatch(html, /dataset-name/)
})

test('이전 username 전용 세션은 재사용하지 않고 실명 미입력 새 세션은 복원한다', t => {
  const entries = sessionStorageForTest(t)
  entries.set('image-ai-platform.auth', JSON.stringify({ accessToken: 'old-token', tokenType: 'Bearer',
    expiresIn: 900, expiresAt: Date.now() + 900000, username: 'old-login' }))
  assert.equal(loadAuthSession(), null)
  assert.equal(entries.size, 0)
  saveAuthSession({ accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 900,
    userKey: 'stable-key', userId: 'login-id', username: null })
  assert.equal(loadAuthSession().userKey, 'stable-key')
  assert.equal(loadAuthSession().userId, 'login-id')
  assert.equal(loadAuthSession().username, null)
})

test('데이터셋 생성은 입력값과 Bearer 토큰을 보내고 서버 생성 정보를 반환한다', async t => {
  const dataset = { datasetId: 'generated-id', name: '학습 이미지', description: null,
    createdAt: '2026-11-10T10:00:00.123456Z', updatedAt: '2026-11-10T10:00:00.123456Z', createdBy: 'admin-key', updatedBy: 'admin-key' }
  const copies = [{ ...dataset, workspaceId: 'space-a' }, { ...dataset, datasetId: 'second-id', workspaceId: 'space-b' }]
  const request = t.mock.method(globalThis, 'fetch', async () => Response.json(copies, { status: 201 }))
  assert.deepEqual(await createDataset({ name: '학습 이미지', description: null, workspaceIds: ['space-a', 'space-b'] }, 'access-token'), copies)
  const [url, options] = request.mock.calls[0].arguments
  assert.equal(url, '/api/datasets')
  assert.equal(options.method, 'POST')
  assert.equal(options.headers.Authorization, 'Bearer access-token')
  assert.deepEqual(JSON.parse(options.body), { name: '학습 이미지', description: null, workspaceIds: ['space-a', 'space-b'] })
})

test('데이터셋 생성 실패는 상태별로 안내하고 내부 오류 본문을 노출하지 않는다', async t => {
  const messages = new Map([
    [400, /워크스페이스를 선택/], [401, /다시 로그인/], [403, /생성 권한이 없거나/], [500, /생성하지 못했습니다/],
  ])
  const response = t.mock.method(globalThis, 'fetch', async () => new Response('private details', { status: 400 }))
  for (const [status, message] of messages) {
    response.mock.mockImplementation(async () => new Response('private details', { status }))
    await assert.rejects(createDataset({ name: 'name', description: null }, 'token'), message)
  }
  response.mock.mockImplementation(async () => { throw new TypeError('fetch failed') })
  await assert.rejects(createDataset({ name: 'name', description: null }, 'token'), /서버에 연결할 수 없습니다/)
})
