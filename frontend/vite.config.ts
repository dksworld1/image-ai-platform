import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const apiProxy = {
  '/api': {
    target: process.env.API_PROXY_TARGET ?? 'http://localhost:8080',
    changeOrigin: true,
  },
}

export default defineConfig({
  plugins: [react()],
  server: { proxy: apiProxy },
  preview: {
    host: process.env.VITE_PREVIEW_HOST ?? '127.0.0.1',
    port: Number(process.env.VITE_PREVIEW_PORT ?? 4173),
    strictPort: true,
    proxy: apiProxy,
  },
})
