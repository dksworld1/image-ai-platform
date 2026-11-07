import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const apiProxy = {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
  },
}

export default defineConfig({
  plugins: [react()],
  server: { proxy: apiProxy },
  preview: {
    host: '127.0.0.1',
    port: 4173,
    strictPort: true,
    proxy: apiProxy,
  },
})
