# 보안 규칙

## 인증/인가 [토이 필수]
- JWT 기반 (Access + Refresh Token)
- 알고리즘: **RS256 (비대칭)** — 공개키만 Gateway/서비스에 배포, 개인키는 Auth Service만 보유
- Access Token 30분 / Refresh Token 7일 (Redis 저장)
- **Refresh Token Rotation** — 재발급 시마다 새 Refresh 발급, 이전 토큰 무효화
- **Reuse Detection** — 사용된 Refresh 재사용 감지 시 토큰 패밀리 전체 폐기
- 단명 Access Token(30분)이라 일반 로그아웃은 Refresh 무효화로 충분
- 로그인 실패 응답은 **이메일/비밀번호 구분 없이 통일** (사용자 enumeration 차단)
- API Gateway에서 JWT 서명 검증 후 라우팅

## 키 관리 [운영]
- JWK `kid` 사용, 분기 1회 키 로테이션
- 운영: Vault / AWS KMS

## 서비스 간 통신 보안 [토이 필수]
- 내부 호출도 인증 필수 — Gateway 우회 차단
- 토이: 내부 JWT (Gateway 가 사용자 JWT → 내부 토큰 변환)
- 운영: mTLS — [docs/study/](../docs/study/) 추가 예정 [운영]

## Spring Security
- Security Filter Chain은 서비스별 독립 구성
- CORS Origin 화이트리스트 명시 (`http://localhost:3000` 등) — 와일드카드 금지
- **CSRF 정책**:
  - REST API (Bearer 토큰): CSRF 비활성화
  - Refresh Token 을 httpOnly 쿠키로 운영 시 → `/auth/refresh` 엔드포인트만 **Origin 헤더 검증** + `SameSite=Strict` 쿠키 (CSRF 부분 보호)
- 비밀번호: `BCryptPasswordEncoder`
  - 로컬/개발: cost 10
  - 운영: cost 12 [운영]
- HTTPS 강제 — HTTP 접근 시 308 리다이렉트 또는 거부 [운영]
- Spring Boot Actuator: `/actuator/health` 만 외부 노출, 나머지는 내부 망 또는 인증

## 권한 (RBAC) [토이 필수]
- 역할: `ROLE_USER`, `ROLE_ADMIN`
- 상품 등록/수정/삭제: ADMIN 전용
- 주문 조회/취소: 본인 주문만 접근 가능
- **IDOR 방어** — `path variable` 의 리소스 ID와 JWT subject 일치 검증
- 권한 체크는 항상 서버에서 (UI 숨김 처리는 보안 아님)

## 비밀번호 정책 [토이 필수]
- 최소 8자, 영문 + 숫자 + 특수문자
- 사용자 정보(이메일·이름) 포함 금지
- 동일 문자 4회 이상 반복 금지
- 단일 Validator 클래스에서 일원화

## 로그인 시도 제한 [토이 필수]
- IP + 이메일 조합으로 실패 횟수 카운트 (Redis)
- 5회 연속 실패 → 10분 일시 잠금
- 응답은 `429 Too Many Requests`
- Credential Stuffing 대비: 이메일 단독 카운터 병행 [운영]

## Rate Limiting [운영]
- API Gateway 레벨 (Spring Cloud Gateway + Redis Token Bucket)
- 인증: 분당 100req / 비인증: 분당 30req (IP)
- 상세: [docs/study/rate-limiting.md](../docs/study/rate-limiting.md)

## 응답 헤더 보안 [토이 필수]
- API JSON 응답에 CSP 적용 무의미 — 정적 자산 서버 책임
- API 서버 적용:
  - `X-Content-Type-Options: nosniff`
  - `Strict-Transport-Security: max-age=31536000; includeSubDomains` [운영]
  - `Referrer-Policy: strict-origin`
- Frontend 정적 호스팅 측 CSP, X-Frame-Options
- 상세: [docs/study/response-header-security.md](../docs/study/response-header-security.md)

## 데이터 보안 (OWASP A02·A03) [토이 필수]
- JPA 파라미터는 바인딩 변수 (SQL Injection 차단)
- 사용자 입력은 Controller 진입 시 검증 (XSS, 길이, null)
- 비밀번호/토큰/키는 평문 저장·로그 출력 금지
- 응답 DTO 에서 민감 정보 제외 (Mass Assignment 방어 — Request DTO·Entity 분리)
- 외부 URL 호출 시 SSRF 방어 — 화이트리스트 (OWASP A10) [운영]

## PII / 개인정보 [운영]
- 주소·연락처·생년월일은 DB 저장 시 AES-256 암호화
- 응답·로그 출력 시 마스킹 (`010-****-1234`)
- 보존 기간 정책 (탈퇴 후 30일 후 파기 등)
- 감사 로그: append-only

## 파일 업로드 [토이 필수 / 일부 운영]
- MIME 타입 + 매직 바이트 검증 (확장자 신뢰 금지) [토이 필수]
- 확장자 화이트리스트 (`jpg`, `png`, `webp`) [토이 필수]
- 최대 크기 제한 (예: 5MB) [토이 필수]
- 저장 경로에 사용자 입력 사용 금지 (path traversal) [토이 필수]
- S3 presigned URL + 안티바이러스 [운영]

## 환경변수 / 시크릿
- DB·JWT·Redis 접속정보는 `.env` 또는 환경변수
- `.env` 는 `.gitignore` 포함, 절대 커밋 금지
- 운영: Vault / AWS Secrets Manager [운영]

## 의존성 관리 (OWASP A06)
- Gradle 의존성 명시적 버전 고정
- `OWASP Dependency Check` 정기 실행 (CI 자동화 [운영])
- CVE 발생 시 즉시 업그레이드

## 보안 로깅 (OWASP A09) [토이 필수]
- 로그인 성공/실패 모두 기록
- 권한 거부(`403`) 발생 시 기록
- 관리자 행위 감사 로그 — append-only
- 로그에 비밀번호·토큰·전체 카드번호 출력 금지

## OWASP Top 10 매핑
| OWASP | 대응 규칙 |
|-------|----------|
| A01 Broken Access Control | RBAC + IDOR 방어 + 본인 리소스 검증 |
| A02 Cryptographic Failures | BCrypt, JWT RS256, HTTPS, PII 암호화 |
| A03 Injection | JPA 바인딩, 입력값 검증 |
| A04 Insecure Design | 위협 모델링, mTLS/내부 JWT |
| A05 Security Misconfiguration | Actuator 잠금, 에러 메시지 추상화 |
| A06 Vulnerable Components | 의존성 정기 점검 |
| A07 Auth Failures | Refresh Rotation, 시도 제한, enumeration 차단 |
| A08 Integrity Failures | Kafka 이벤트 멱등 처리, Schema Registry |
| A09 Logging Failures | append-only 감사 로그, 민감정보 마스킹 |
| A10 SSRF | 외부 URL 화이트리스트 |
