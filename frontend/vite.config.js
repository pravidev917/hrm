import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The SPA always calls relative /api paths. In development Vite forwards /api to
// the local Spring Boot process, so no VITE_API_BASE_URL is needed for day-to-day
// work and the browser sees a single origin (no CORS, no cookie problems).
//
// Point it somewhere else when the backend runs on another port:
//   VITE_PROXY_TARGET=http://localhost:8081 npm run dev
const proxyTarget = process.env.VITE_PROXY_TARGET || 'http://localhost:8080'
const usePolling = process.env.VITE_USE_POLLING === '1'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Listen on IPv4 and IPv6. Without this Node picks one family, so one of
    // http://localhost:5173 or http://127.0.0.1:5173 answers and the other is
    // refused - which looks like "the app is down" in some browsers.
    host: '::',
    proxy: {
      '/api': {
        target: proxyTarget,
        changeOrigin: false,
      },
    },
    watch: usePolling ? { usePolling: true, interval: 300 } : undefined,
  },
})
