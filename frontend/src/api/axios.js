import axios from 'axios';

// API Gateway 주소 — 환경별 .env로 관리
const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' },
});

// 요청 인터셉터 — JWT Access Token 자동 첨부
instance.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 인증 없이 호출하는 공개 엔드포인트 — 401 자동 로그인 리다이렉트 제외
const PUBLIC_ENDPOINTS = ['/api/v1/auth/login', '/api/v1/auth/signup', '/api/v1/auth/forgot-password'];

// 응답 인터셉터 — 401 시 토큰 초기화 후 로그인 이동 (공개 엔드포인트 제외)
instance.interceptors.response.use(
  (response) => response,
  (error) => {
    const url = error.config?.url || '';
    const isPublic = PUBLIC_ENDPOINTS.some((p) => url.includes(p));
    if (error.response?.status === 401 && !isPublic) {
      localStorage.removeItem('accessToken');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default instance;
