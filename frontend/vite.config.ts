import { readFileSync } from 'node:fs'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ command }) => {
  const plugins = [react()]
  if (command === 'build') return { plugins }

  const settings = readFileSync(new URL('../bin/local.settings.ps1', import.meta.url), 'utf8')
  const port = Number(settings.match(/^\s*\[int\]\s*\$BackendPort\s*=\s*(\d+)\s*$/m)?.[1])
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error('local.settings.ps1의 BackendPort는 1~65535 사이의 정수여야 합니다.')
  }
  const proxy = { '/api': { target: `http://localhost:${port}`, changeOrigin: true } }
  return {
    plugins,
    server: { proxy },
    preview: { host: '127.0.0.1', port: 4173, strictPort: true, proxy },
  }
})
