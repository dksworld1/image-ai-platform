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
const { fetchImageDatasetTargets, fetchImages, fetchThumbnail, fetchImageContent, uploadImage, ImageUploadError, deleteImage, saveImageDescriptions } = require('../src/api/images.ts')
const { getEntryDescriptionChanges } = require('../src/pages/DatasetManagePage.tsx')
const { entryApis } = require('../src/api/datasetEntries.ts')
const { uploadFile } = require('../src/api/files.ts')
const DatasetUploadModal = require('../src/pages/DatasetUploadModal.tsx').default
const { droppedUploadFiles } = require('../src/pages/droppedUploadFiles.ts')
const DatasetManagePage = require('../src/pages/DatasetManagePage.tsx').default
const App = require('../src/App.tsx').default
const { saveAuthSession, loadAuthSession, clearAuthSession } = require('../src/authSession.ts')

test('폴더 드롭은 여러 차례 읽은 하위 파일의 전체 경로를 보존한다', async () => {
  const fileEntry = name => ({ name, isFile: true, isDirectory: false, file: resolve => resolve(new File(['bytes'], name)) })
  const folderEntry = (name, batches) => ({ name, isFile: false, isDirectory: true, createReader: () => {
    let index = 0
    return { readEntries: resolve => resolve(batches[index++] ?? []) }
  } })
  const folder = folderEntry('선택폴더', [[fileEntry('a.png')], [folderEntry('하위', [[fileEntry('a.png')]])]])
  const loose = fileEntry('별도.csv')
  const files = await droppedUploadFiles({ items: [folder, loose].map(entry => ({ kind: 'file', webkitGetAsEntry: () => entry })), files: [] })
  assert.deepEqual(files.map(file => file.webkitRelativePath || file.name), ['선택폴더/a.png', '선택폴더/하위/a.png', '별도.csv'])
  assert.equal(await files[1].text(), 'bytes')
  const fallback = new File(['x'], '단일.txt')
  assert.deepEqual(await droppedUploadFiles({ items: [], files: [fallback] }), [fallback])
})

test('폴더 드롭은 읽기 실패를 알리고 빈 폴더는 파일을 추가하지 않는다', async () => {
  const directory = reader => ({ kind: 'file', webkitGetAsEntry: () => ({ name: '폴더', isDirectory: true, createReader: () => reader }) })
  assert.deepEqual(await droppedUploadFiles({ items: [directory({ readEntries: resolve => resolve([]) })], files: [] }), [])
  await assert.rejects(droppedUploadFiles({ items: [directory({ readEntries: (_, reject) => reject(new Error('접근 실패')) })], files: [] }), /접근 실패/)
})

test('폴더 업로드는 두 파일 API 모두 선택 폴더부터의 상대 경로를 보낸다', async t => {
  const request = t.mock.method(globalThis, 'fetch', async () => Response.json({}))
  for (const upload of [uploadImage, uploadFile]) {
    const file = new File(['content'], 'sample.png')
    Object.defineProperty(file, 'webkitRelativePath', { value: '선택폴더/하위/sample.png' })
    await upload('dataset', file, 'token')
    const body = request.mock.calls.at(-1).arguments[1].body
    assert.equal(body.get('relativePath'), '선택폴더/하위/sample.png')
    assert.equal(body.get('file').name, 'sample.png')
  }
  for (const kind of ['images', 'files']) {
    const markup = renderToStaticMarkup(React.createElement(DatasetUploadModal, { dataset: {}, accessToken: 'token', kind }))
    assert.match(markup, /업로드할 폴더 선택/)
    assert.match(markup, /선택한 폴더 이름부터의 경로/)
    assert.match(markup, /<button[^>]*>파일 선택<\/button>/)
    assert.match(markup, /<button[^>]*>폴더 선택<\/button>/)
    assert.match(markup, /파일 또는 폴더를 여기에 놓으세요/)
    assert.equal((markup.match(/type="file" hidden=""/g) ?? []).length, 2)
  }
})

test('데이터 파일 탭은 파일 전용 경로와 fileId를 사용하고 공통 목록 ID로 변환한다', async t => {
  const target = { datasetId: 'dataset/a', name: '공통', workspaceId: 'space', workspaceName: '공간' }
  const request = t.mock.method(globalThis, 'fetch', async () => Response.json([target]))
  assert.deepEqual(await entryApis.files.targets('token'), [target])
  assert.equal(request.mock.calls[0].arguments[0], '/api/datasets/file-targets')
  const file = { fileId: 'file/1', datasetId: 'dataset/a', originalFilename: '자료.csv', description: null }
  request.mock.mockImplementation(async () => Response.json([file]))
  const list = await entryApis.files.list('dataset/a', 'token')
  assert.equal(list[0].entryId, 'file/1')
  assert.equal(list[0].thumbnailUrl, null)
  assert.equal(request.mock.calls[1].arguments[0], '/api/datasets/dataset%2Fa/files')
  await entryApis.files.save('dataset/a', [{ entryId: 'file/1', description: '편집' }], 'token')
  const [, options] = request.mock.calls[2].arguments
  assert.equal(options.headers.Authorization, 'Bearer token')
  assert.deepEqual(JSON.parse(options.body), { items: [{ fileId: 'file/1', description: '편집' }] })
  request.mock.mockImplementation(async () => new Response(null, { status: 204 }))
  await entryApis.files.remove('dataset/a', 'file/1', 'token')
  assert.equal(request.mock.calls[3].arguments[0], '/api/datasets/dataset%2Fa/files/file%2F1')
})

test('데이터 파일 업로드는 파일별 설명과 수정 모드를 별도 files API로 전송한다', async t => {
  const request = t.mock.method(globalThis, 'fetch', async () => Response.json({ fileId: 'file' }, { status: 201 }))
  const file = new File(['a,b'], '자료.csv')
  await uploadFile('dataset', file, 'token', undefined, true, '설명')
  const [url, options] = request.mock.calls[0].arguments
  assert.equal(url, '/api/datasets/dataset/files?replaceExisting=true')
  assert.equal(options.body.get('file').name, '자료.csv')
  assert.equal(options.body.get('description'), '설명')
  request.mock.mockImplementation(async () => new Response('', { status: 403 }))
  await assert.rejects(uploadFile('dataset', file, 'token'), /권한/)
})

test('데이터셋 관리 화면은 두 파일 탭과 직접 진입한 데이터 파일 선택 상태를 제공한다', t => {
  const previous = globalThis.window
  globalThis.window = { location: { search: '?tab=files' } }
  t.after(() => { if (previous === undefined) delete globalThis.window; else globalThis.window = previous })
  const markup = renderToStaticMarkup(React.createElement(DatasetManagePage, { accessToken: 'token' }))
  assert.match(markup, /데이터셋 관리/)
  assert.match(markup, /id="dataset-tab-files"[^>]*aria-selected="true"/)
  assert.match(markup, /이미지 파일/)
  assert.match(markup, /데이터 파일/)
})

test('이미지 설명 저장 대상은 선택된 실제 변경 행만 포함하고 공백 설명은 지운다', () => {
  const images = [{ entryId: 'a', description: '원래' }, { entryId: 'b', description: '그대로' }, { entryId: 'c', description: '미선택' }, { entryId: 'd', description: '삭제' }]
  const drafts = { a: '변경', b: '그대로', c: '선택 해제한 변경', d: '  ' }
  const changes = getEntryDescriptionChanges(images, drafts, ['a', 'b', 'd'])
  assert.deepEqual(changes, [{ entryId: 'a', description: '변경' }, { entryId: 'd', description: null }])
  assert.deepEqual(getEntryDescriptionChanges(images, drafts, []), [])
})

test('이미지 설명은 한 PATCH로 저장하고 실패 응답을 전체 저장 성공으로 처리하지 않는다', async t => {
  const items = [{ imageId: 'image', description: '설명' }]
  const request = t.mock.method(globalThis, 'fetch', async () => Response.json(items))
  assert.deepEqual(await saveImageDescriptions('dataset/a', items, 'token'), items)
  const [url, options] = request.mock.calls[0].arguments
  assert.equal(url, '/api/datasets/dataset%2Fa/images/descriptions')
  assert.equal(options.method, 'PATCH')
  assert.equal(options.headers.Authorization, 'Bearer token')
  assert.deepEqual(JSON.parse(options.body), { items })
  for (const [status, message] of [[400, /2,000자/], [403, /이미지 수정 권한/], [404, /전체 저장이 취소/], [500, /저장하지 못했습니다/]]) {
    request.mock.mockImplementation(async () => new Response('', { status }))
    await assert.rejects(saveImageDescriptions('dataset', items, 'token'), message)
  }
})

test('이미지 삭제는 선택 이미지 경로·인증을 사용하고 실패를 성공으로 처리하지 않는다', async t => {
  const request = t.mock.method(globalThis, 'fetch', async () => new Response(null, { status: 204 }))
  const controller = new AbortController()
  await deleteImage('dataset/a', 'image/1', 'token', controller.signal)
  const [url, options] = request.mock.calls[0].arguments
  assert.equal(url, '/api/datasets/dataset%2Fa/images/image%2F1')
  assert.equal(options.method, 'DELETE')
  assert.equal(options.headers.Authorization, 'Bearer token')
  assert.equal(options.signal, controller.signal)
  for (const [status, message] of [[401, /다시 로그인/], [403, /삭제 권한/], [404, /이미 삭제되었거나 교체된/], [500, /삭제하지 못했습니다/]]) {
    request.mock.mockImplementation(async () => new Response('private details', { status }))
    await assert.rejects(deleteImage('dataset', 'image', 'token'), message)
  }
  const calls = request.mock.calls.length
  request.mock.mockImplementation(async () => { throw new TypeError('fetch failed') })
  await assert.rejects(deleteImage('dataset', 'image', 'token'), /삭제 결과를 확인할 수 없습니다/)
  assert.equal(request.mock.calls.length, calls + 1)
})

test('이미지 대상 목록은 데이터셋 목록 대신 이미지 권한 전용 경로로 조회한다', async t => {
  const targets = [{ datasetId: 'd1', name: '이미지 대상', workspaceId: 'a', workspaceName: '공간' }]
  const request = t.mock.method(globalThis, 'fetch', async () => Response.json(targets))
  const controller = new AbortController()
  assert.deepEqual(await fetchImageDatasetTargets('token', controller.signal), targets)
  const [url, options] = request.mock.calls[0].arguments
  assert.equal(url, '/api/datasets/image-targets')
  assert.equal(options.headers.Authorization, 'Bearer token')
  assert.equal(options.signal, controller.signal)
  request.mock.mockImplementation(async () => new Response('', { status: 403 }))
  await assert.rejects(fetchImageDatasetTargets('token'), /이미지를 조회할 권한/)
})

test('이미지 업로드는 multipart 단건 파일과 인증을 보내고 원본명을 보존한다', async t => {
  const result = { imageId: 'image', originalFilename: '원본.png' }
  const request = t.mock.method(globalThis, 'fetch', async () => Response.json(result, { status: 201 }))
  const file = new File(['image'], '원본.png', { type: 'image/png' })
  const controller = new AbortController()
  assert.deepEqual(await uploadImage('dataset/a', file, 'token', controller.signal, false, '파일별 설명'), result)
  const [url, options] = request.mock.calls[0].arguments
  assert.equal(url, '/api/datasets/dataset%2Fa/images')
  assert.equal(options.method, 'POST')
  assert.deepEqual(options.headers, { Authorization: 'Bearer token' })
  assert.equal(options.signal, controller.signal)
  assert.equal(options.body.get('file').name, file.name)
  assert.equal(await options.body.get('file').text(), 'image')
  assert.equal(options.body.get('description'), '파일별 설명')
  await uploadImage('dataset/a', file, 'token', controller.signal, true)
  assert.equal(request.mock.calls[1].arguments[0], '/api/datasets/dataset%2Fa/images?replaceExisting=true')
})

test('이미지 업로드는 중복·용량·권한 오류를 구분하고 불명확한 결과를 재전송하지 않는다', async t => {
  const request = t.mock.method(globalThis, 'fetch', async () => new Response('private details', { status: 409 }))
  const file = new File(['image'], '원본.png')
  for (const [status, message] of [[400, /파일명/], [401, /다시 로그인/], [403, /업로드 권한/], [409, /이미 존재하는 이미지/], [413, /용량 제한/], [500, /등록 결과를 확인/]]) {
    request.mock.mockImplementation(async () => new Response('private details', { status }))
    await assert.rejects(uploadImage('dataset', file, 'token'), error => error instanceof ImageUploadError && error.status === status && message.test(error.message))
  }
  const previousCalls = request.mock.calls.length
  request.mock.mockImplementation(async () => { throw new TypeError('fetch failed') })
  await assert.rejects(uploadImage('dataset', file, 'token'), error => error instanceof ImageUploadError && error.status === null)
  assert.equal(request.mock.calls.length, previousCalls + 1)
})

test('이미지 목록과 썸네일은 데이터셋 ID를 인코딩하고 인증 헤더와 취소 신호를 전달한다', async t => {
  const image = { imageId: 'image/1', datasetId: 'dataset/a', thumbnailUrl: '/untrusted-path' }
  const request = t.mock.method(globalThis, 'fetch', async () => Response.json([image]))
  const controller = new AbortController()
  assert.deepEqual(await fetchImages('dataset/a', 'token', controller.signal), [image])
  let [url, options] = request.mock.calls[0].arguments
  assert.equal(url, '/api/datasets/dataset%2Fa/images')
  assert.equal(options.headers.Authorization, 'Bearer token')
  assert.equal(options.signal, controller.signal)
  request.mock.mockImplementation(async () => new Response('thumbnail', { headers: { 'Content-Type': 'image/png' } }))
  const blob = await fetchThumbnail(image.datasetId, image.imageId, 'token', controller.signal)
  ;[url, options] = request.mock.calls[1].arguments
  assert.equal(url, '/api/datasets/dataset%2Fa/images/image%2F1/thumbnail')
  assert.equal(options.headers.Authorization, 'Bearer token')
  assert.equal(options.signal, controller.signal)
  assert.equal(blob.type, 'image/png')
  const original = await fetchImageContent(image.datasetId, image.imageId, 'token', controller.signal)
  ;[url, options] = request.mock.calls[2].arguments
  assert.equal(url, '/api/datasets/dataset%2Fa/images/image%2F1/content')
  assert.equal(options.headers.Authorization, 'Bearer token')
  assert.equal(options.signal, controller.signal)
  assert.equal(original.type, 'image/png')
})

test('이미지 조회 거부와 썸네일 없음을 구분하고 취소는 그대로 전달한다', async t => {
  const request = t.mock.method(globalThis, 'fetch', async () => new Response('private details', { status: 403 }))
  const controller = new AbortController()
  await assert.rejects(fetchImages('dataset', 'token'), /조회 권한/)
  await assert.rejects(fetchThumbnail('dataset', 'image', 'token', controller.signal), /조회 권한/)
  await assert.rejects(fetchImageContent('dataset', 'image', 'token', controller.signal), /조회 권한/)
  request.mock.mockImplementation(async () => new Response('', { status: 404 }))
  await assert.rejects(fetchThumbnail('dataset', 'image', 'token', controller.signal), /썸네일 없음/)
  await assert.rejects(fetchImageContent('dataset', 'image', 'token', controller.signal), /이미지를 찾을 수 없습니다/)
  request.mock.mockImplementation(async () => new Response('', { status: 401 }))
  await assert.rejects(fetchImages('dataset', 'token'), /다시 로그인/)
  controller.abort()
  request.mock.mockImplementation(async () => { throw controller.signal.reason })
  await assert.rejects(fetchImages('dataset', 'token', controller.signal), reason => reason === controller.signal.reason)
})

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
