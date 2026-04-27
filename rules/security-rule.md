# 보안 규칙

## 인증/인가
- 인증은 JWT 기반 (Access Token + Refresh Token)
- Access Token 유효기간: 30분
- Refresh Token 유효기간: 7일, Redis에 저장
- 로그아웃 시 Access Token → Redis 블랙리스트 등록
- API Gateway에서 JWT 서명 검증 후 라우팅

## Spring Security
- Security Filter Chain은 서비스별로 독립 구성
- CORS 설정은 Gateway에서만 허용, 각 서비스는 Gateway만 신뢰
- CSRF 비활성화 (Stateless REST API)
- 비밀번호는 `BCryptPasswordEncoder` 로 암호화

## 데이터 보안
- JPA 파라미터는 바인딩 변수 사용 (문자열 직접 치환 금지 — SQL Injection)
- 사용자 입력값은 Controller 진입 시점에 검증 (XSS, 길이, null 체크)
- 비밀번호/토큰/키는 평문 저장 및 로그 출력 금지
- 민감 정보는 응답 DTO에서 제외 (비밀번호 필드 등)

## 환경변수
- DB 접속정보, JWT Secret, Redis 접속정보는 `.env` 파일로 관리
- `.env` 파일은 `.gitignore` 에 포함 — 절대 커밋 금지
- `application.yml` 에 민감 정보 하드코딩 금지

## 권한 (RBAC)
- 역할: `ROLE_USER`, `ROLE_ADMIN`
- 상품 등록/수정/삭제: ADMIN 전용
- 주문 조회/취소: 본인 주문만 접근 가능
