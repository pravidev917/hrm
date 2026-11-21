import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The SPA always calls relative /api paths (same-origin in production, where
// nginx proxies to Spring Boot). In development Vite forwards /api to the local
// backend, so no VITE_API_BASE_URL is needed for day-to-day work.
// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
})
