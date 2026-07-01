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
        // 외부 IP(10.30.x.x 등)에서 접근 시 Origin 헤더를 localhost로 변환
        // → 게이트웨이 CORS 허용 목록(localhost:5173)에 매칭
        headers: { origin: 'http://localhost:5173' },
      },
    },
  },
})
