import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The SPA always calls relative /api paths (same-origin in production, where
// nginx proxies to Spring Boot). In development Vite forwards /api to the local
// backend, so no VITE_API_BASE_URL is needed for day-to-day work.
//
// Docker dev stack (docker-compose.dev.yml) overrides both settings, because the
// backend is a sibling container and bind mounts on Windows/macOS do not emit
// file-system events reliably:
//   VITE_PROXY_TARGET=http://backend:8080
//   VITE_USE_POLLING=1
const proxyTarget = process.env.VITE_PROXY_TARGET || 'http://localhost:8080'
const usePolling = process.env.VITE_USE_POLLING === '1'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: proxyTarget,
        changeOrigin: false,
      },
    },
    watch: usePolling ? { usePolling: true, interval: 300 } : undefined,
  },
})
