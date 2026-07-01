/**
 * CR-05: Access Token 메모리 전용 저장소
 *
 * localStorage 저장 시 XSS 공격으로 토큰 탈취 가능 → 메모리(모듈 변수)에만 보관.
 * 페이지 새로고침 시 /api/v1/auth/refresh 로 복원 (authStore.tryRestoreAuth 참조).
 *
 * TODO: Refresh Token은 auth-service에서 HttpOnly; Secure; SameSite=Strict 쿠키로
 *       발급해야 완전한 보안이 달성됩니다. (auth-service 작업 시 함께 적용 예정)
 */
let _accessToken = null;

export const setToken  = (token) => { _accessToken = token; };
export const getToken  = ()      => _accessToken;
export const clearToken = ()     => { _accessToken = null; };
