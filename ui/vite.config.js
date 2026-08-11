import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig(({ mode }) => ({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: [
      // Demo build: swap the HTTP api module for the localStorage adapter.
      // Must precede the '@' alias so '@/services/api' matches first.
      ...(mode === 'demo'
        ? [{ find: '@/services/api', replacement: fileURLToPath(new URL('./src/demo/demoApi.js', import.meta.url)) }]
        : []),
      { find: '@', replacement: fileURLToPath(new URL('./src', import.meta.url)) },
    ],
  },
  server: {
    proxy: {
      '/api': 'http://localhost:9090',
    },
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.js'],
  },
}))
