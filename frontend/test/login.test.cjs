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
const App = require('../src/App.tsx').default
const Navigation = require('../src/layout/Navigation.tsx').default
const { fetchNavigation } = require('../src/api/ui.ts')
const { saveAuthSession, loadAuthSession, clearAuthSession } = require('../src/authSession.ts')

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
  const token = { accessToken: 'test-token', tokenType: 'Bearer', expiresIn: 900 }
  const request = t.mock.method(globalThis, 'fetch', async () => Response.json(token))
  assert.deepEqual(await login('tester', ' secret '), token)
  const [url, options] = request.mock.calls[0].arguments
  assert.equal(url, '/api/auth/token')
  assert.equal(options.method, 'POST')
  assert.deepEqual(options.headers, { 'Content-Type': 'application/json' })
  assert.deepEqual(JSON.parse(options.body), { username: 'tester', password: ' secret ' })
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

test('로그인 토큰으로 내비게이션을 조회한다', async t => {
  const navigation = { screens: [], menus: [] }
  const request = t.mock.method(globalThis, 'fetch', async () => Response.json(navigation))
  assert.deepEqual(await fetchNavigation('test-token'), navigation)
  const [url, options] = request.mock.calls[0].arguments
  assert.equal(url, '/api/ui/navigation')
  assert.equal(options.headers.Authorization, 'Bearer test-token')
})

test('부모가 뒤에 있는 메뉴도 계층과 형제 순서대로 표시한다', () => {
  const screens = [
    { screenCode: 'home', routePath: '/', displayName: '시작 화면' },
    { screenCode: 'screens', routePath: '/admin/screens', displayName: '화면 관리' },
    { screenCode: 'menus', routePath: '/admin/menus', displayName: '메뉴 관리' },
  ]
  const menus = [
    { menuCode: 'z', parentMenuCode: 'admin', nodeType: 'SCREEN', displayName: '메뉴 관리', screenCode: 'menus', sortOrder: 20 },
    { menuCode: 'a', parentMenuCode: 'admin', nodeType: 'SCREEN', displayName: '화면 관리', screenCode: 'screens', sortOrder: 10 },
    { menuCode: 'admin', parentMenuCode: null, nodeType: 'GROUP', displayName: '관리자', screenCode: null, sortOrder: 100 },
    { menuCode: 'home', parentMenuCode: null, nodeType: 'SCREEN', displayName: '시작 화면', screenCode: 'home', sortOrder: 0 },
  ]
  const html = renderToStaticMarkup(React.createElement(Navigation, { navigation: { screens, menus } }))
  assert.ok(html.indexOf('시작 화면') < html.indexOf('관리자'))
  assert.ok(html.indexOf('관리자') < html.indexOf('화면 관리'))
  assert.ok(html.indexOf('화면 관리') < html.indexOf('메뉴 관리'))
  assert.match(html, /aria-current="page"/)
  assert.equal((html.match(/준비 중/g) || []).length, 2)
  assert.doesNotMatch(html, /href="\/admin/)
})

test('허용 메뉴가 없으면 빈 메뉴 안내를 표시한다', () => {
  const html = renderToStaticMarkup(React.createElement(Navigation, { navigation: { screens: [], menus: [] } }))
  assert.match(html, /표시할 메뉴가 없습니다/)
})

test('내비게이션 인증 만료와 서버·연결 실패를 안내한다', async t => {
  const request = t.mock.method(globalThis, 'fetch', async () => new Response('internal', { status: 401 }))
  await assert.rejects(fetchNavigation('expired'), /다시 로그인/)
  request.mock.mockImplementation(async () => new Response('internal', { status: 500 }))
  await assert.rejects(fetchNavigation('test-token'), /메뉴를 불러오지 못했습니다/)
  request.mock.mockImplementation(async () => { throw new TypeError('fetch failed') })
  await assert.rejects(fetchNavigation('test-token'), /서버에 연결할 수 없습니다/)
})

test('저장한 토큰을 새 앱 진입에서 복원하고 로그아웃 버튼을 표시한다', t => {
  const entries = sessionStorageForTest(t)
  t.mock.method(Date, 'now', () => 1000)
  const session = saveAuthSession({ accessToken: 'saved-token', tokenType: 'Bearer', expiresIn: 900 }, 'admin')
  assert.equal(session.username, 'admin')
  assert.equal(session.expiresAt, 901000)
  assert.deepEqual(loadAuthSession(), session)
  assert.equal(entries.size, 1)
  const html = renderToStaticMarkup(React.createElement(App))
  assert.match(html, /프로젝트 시작 화면/)
  assert.match(html, /로그인 계정/)
  assert.match(html, />admin</)
  assert.match(html, /<button[^>]*>로그아웃<\/button>/)
  assert.doesNotMatch(html, /type="password"/)
})

test('로그아웃으로 저장 토큰을 제거하면 다시 로그인 화면을 표시한다', t => {
  const entries = sessionStorageForTest(t)
  saveAuthSession({ accessToken: 'saved-token', tokenType: 'Bearer', expiresIn: 900 }, 'admin')
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
  saveAuthSession({ accessToken: 'saved-token', tokenType: 'Bearer', expiresIn: 1 }, 'admin')
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
