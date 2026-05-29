import { create } from 'zustand';
import { setToken, clearToken } from './tokenStore';
import api from '../api/axios';

// JWT payload 디코딩 (검증 없이 클레임만 파싱)
const decodeJwt = (token) => {
  try {
    return JSON.parse(atob(token.split('.')[1]));
  } catch {
    return null;
  }
};

const UNAUTHENTICATED = { isAuthenticated: false, userId: null, role: null, pwdChangeRequired: false };

const useAuthStore = create((set) => ({
  // CR-05: 초기 상태는 항상 미인증 — localStorage 미사용
  //        페이지 새로고침 시 tryRestoreAuth()가 refresh 토큰으로 복원
  ...UNAUTHENTICATED,

  login: (token) => {
    setToken(token); // 메모리에만 저장
    const claims = decodeJwt(token);
    set({
      isAuthenticated: true,
      userId: claims?.sub ?? null,
      role: claims?.role ?? null,
      pwdChangeRequired: claims?.pwdChangeRequired ?? false,
    });
  },

  logout: () => {
    clearToken();
    set(UNAUTHENTICATED);
  },

  /**
   * 페이지 새로고침 후 세션 복원 시도.
   * auth-service의 refresh 토큰(HttpOnly 쿠키)으로 새 access token 발급.
   * 실패 시 미인증 상태 유지.
   */
  tryRestoreAuth: async () => {
    try {
      const res = await api.post('/api/v1/auth/refresh');
      const { accessToken } = res.data;
      if (accessToken) {
        setToken(accessToken);
        const claims = decodeJwt(accessToken);
        set({
          isAuthenticated: true,
          userId: claims?.sub ?? null,
          role: claims?.role ?? null,
          pwdChangeRequired: claims?.pwdChangeRequired ?? false,
        });
      }
    } catch {
      // refresh 실패 → 미인증 유지 (로그인 필요)
      set(UNAUTHENTICATED);
    }
  },
}));

export default useAuthStore;
