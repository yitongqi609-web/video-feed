import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发期 API 走 Vite 代理，后端无需 CORS 配置
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/media': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
