import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// The SPA always calls relative /api paths. In development Vite forwards /api to
// the local Spring Boot process, so no VITE_API_BASE_URL is needed for day-to-day
// work and the browser sees a single origin (no CORS, no cookie problems).
//
// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // Vite does not load .env files before evaluating its config. Load all
  // variables here so VITE_PROXY_TARGET and VITE_USE_POLLING work from
  // frontend/.env as well as from the shell.
  const env = loadEnv(mode, process.cwd(), '')
  const proxyTarget = env.VITE_PROXY_TARGET || 'http://localhost:8080'
  const usePolling = env.VITE_USE_POLLING === '1'

  return {
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
  }
})
