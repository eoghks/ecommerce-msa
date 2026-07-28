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

// 세션 복원 single-flight 가드.
// StrictMode(dev) 이중 호출·동시 진입 시 refresh 요청이 2번 나가면,
// H-B(1회용 refresh 토큰 회전)로 인해 두 번째 요청이 401 → 로그아웃 처리되는 경쟁을 막는다.
// 진행 중인 복원이 있으면 같은 Promise를 재사용해 refresh를 정확히 1회만 호출한다.
let restoreInFlight = null;

const useAuthStore = create((set) => ({
  // CR-05: 초기 상태는 항상 미인증 — localStorage 미사용
  //        페이지 새로고침/새 탭 시 tryRestoreAuth()가 refresh 쿠키로 복원
  ...UNAUTHENTICATED,
  // 초기 세션 복원 시도 완료 여부 — 완료 전엔 라우트 판단 보류 (깜빡임/오리다이렉트 방지)
  authResolved: false,

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
    // 이미 복원 진행 중이면 같은 요청을 재사용 (refresh 중복 호출 방지)
    if (restoreInFlight) return restoreInFlight;

    restoreInFlight = (async () => {
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
            authResolved: true,
          });
          return;
        }
        set({ ...UNAUTHENTICATED, authResolved: true });
      } catch {
        // refresh 실패 → 미인증 유지 (로그인 필요)
        set({ ...UNAUTHENTICATED, authResolved: true });
      } finally {
        restoreInFlight = null;
      }
    })();

    return restoreInFlight;
  },
}));

export default useAuthStore;
