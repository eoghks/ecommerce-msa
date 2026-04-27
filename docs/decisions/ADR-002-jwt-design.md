# JWT 설계

> **상태**: 🔲 템플릿 — Week 2 (Auth Service 구현 시 작성)

---

## 배경
- 인증 방식 선택 이유 (Session vs JWT)
- MSA 환경에서 JWT가 적합한 이유

## 후보 비교
- HS256 (대칭) vs RS256 (비대칭) vs ES256 (타원곡선)
- 비교 항목: 키 관리, 검증 분산성, 성능, 보안

## 선택과 근거
- 어떤 알고리즘을 선택했고 왜
- 토큰 구조 (claims 설계)
- 만료 정책 (Access 30분 / Refresh 7일)

## 적용 방식
- Auth Service: 토큰 발급 흐름
- Gateway: 토큰 검증 흐름
- Refresh Token 저장 전략 (Redis)
- 로그아웃 시 블랙리스트 처리

## 한계 및 향후 개선
- 토큰 탈취 대응 (재발급 시 Refresh 무효화)
- 키 로테이션 전략 부재
- 운영 시 Vault/KMS 도입 고려

## 면접 답변 시나리오
> Q: 왜 JWT를 선택했나요?
> A:

> Q: RS256과 HS256 차이는?
> A:

> Q: 토큰 탈취 대응은?
> A:
