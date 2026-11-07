import assert from 'node:assert/strict'
import { once } from 'node:events'
import { copyFile, mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises'
import { createServer as httpServer } from 'node:http'
import { join } from 'node:path'
import { test } from 'node:test'
import { createServer, preview } from 'vite'

test('dev와 preview가 BackendPort로 /api 요청을 전달한다', async (t) => {
  const backend = httpServer((req, res) => res.end(req.url))
  backend.listen(0)
  await once(backend, 'listening')
  t.after(() => new Promise(resolve => backend.close(resolve)))

  const fixture = await mkdtemp(join(import.meta.dirname, 'proxy-'))
  t.after(() => rm(fixture, { recursive: true, force: true }))
  const root = join(fixture, 'frontend')
  await mkdir(join(root, 'dist'), { recursive: true })
  await mkdir(join(fixture, 'bin'))
  await writeFile(join(fixture, 'bin/local.settings.ps1'), `[int] $BackendPort = ${backend.address().port}\n`)
  const configFile = join(root, 'vite.config.ts')
  await copyFile(new URL('../vite.config.ts', import.meta.url), configFile)
  const address = { host: '127.0.0.1', port: 0 }
  for (const mode of ['dev', 'preview']) {
    const options = { root, configFile, server: address, preview: address }
    const vite = mode === 'dev' ? await createServer(options) : await preview(options)
    try {
      if (mode === 'dev') await vite.listen()
      const response = await fetch(`http://127.0.0.1:${vite.httpServer.address().port}/api/health`)
      assert.equal(await response.text(), '/api/health')
    } finally {
      await vite.close()
    }
  }
})
