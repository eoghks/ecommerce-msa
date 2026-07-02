/**
 * CR-05: Access Token 메모리 전용 저장소
 *
 * localStorage 저장 시 XSS 공격으로 토큰 탈취 가능 → 메모리(모듈 변수)에만 보관.
 * 페이지 새로고침 시 /api/v1/auth/refresh 로 복원 (authStore.tryRestoreAuth 참조).
 *
 * Refresh Token은 auth-service에서 HttpOnly; Secure; SameSite 쿠키로 발급되며,
 * axios(withCredentials: true)가 자동 송수신한다. (구현 완료)
 */
let _accessToken = null;

export const setToken  = (token) => { _accessToken = token; };
export const getToken  = ()      => _accessToken;
export const clearToken = ()     => { _accessToken = null; };
