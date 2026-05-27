import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',   // 모든 네트워크 인터페이스에서 접근 허용
    proxy: {
      // /api 로 시작하는 모든 요청을 게이트웨이(8080)로 포워딩
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
