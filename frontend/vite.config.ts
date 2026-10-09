import { fileURLToPath, URL } from 'node:url'

import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  // Vite exposes .env to client code, not process.env, so a non-default API port would silently 404.
  const env = loadEnv(mode, process.cwd(), '')
  // BACKEND_PORT lives in the repo root's .env, which compose reads; a host-run dev server must read it too.
  const rootEnv = loadEnv(mode, fileURLToPath(new URL('..', import.meta.url)), '')
  const backendPort = env.BACKEND_PORT ?? rootEnv.BACKEND_PORT ?? '8080'
  const proxyTarget = env.VITE_PROXY_TARGET ?? `http://localhost:${backendPort}`

  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    server: {
      host: true,
      port: 5173,
      proxy: {
        '/api': { target: proxyTarget, changeOrigin: true },
      },
    },
  }
})
