import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('element-plus')) return 'element-plus'
            if (id.includes('@element-plus/icons-vue')) return 'element-plus-icons'
            if (id.includes('vue-router')) return 'vue-router'
            if (id.includes('pinia')) return 'pinia'
            if (id.includes('axios')) return 'axios'
            return 'vendor'
          }
        },
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:10102',
        changeOrigin: true,
      },
      '/doc.html': {
        target: 'http://localhost:10102',
        changeOrigin: true,
      },
      '/v3/api-docs': {
        target: 'http://localhost:10102',
        changeOrigin: true,
      },
      '/swagger-resources': {
        target: 'http://localhost:10102',
        changeOrigin: true,
      },
      '/swagger-ui': {
        target: 'http://localhost:10102',
        changeOrigin: true,
      },
      '/webjars': {
        target: 'http://localhost:10102',
        changeOrigin: true,
      },
    },
  },
})
