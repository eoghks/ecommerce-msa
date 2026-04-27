# 보안 규칙

## 인증/인가
- JWT 기반 (Access + Refresh Token)
- 알고리즘: **RS256 (비대칭)** — 공개키만 Gateway/서비스에 배포, 개인키는 Auth Service만 보유
- Access Token 30분 / Refresh Token 7일 (Redis 저장)
- **Refresh Token Rotation** — 재발급 시마다 새 Refresh 발급, 이전 토큰 무효화
- **Reuse Detection** — 사용된 Refresh 재사용 감지 시 해당 토큰 패밀리 전체 폐기
- 단명 Access Token(30분)이라 일반 로그아웃은 Refresh 무효화로 충분, 블랙리스트는 보안 사고 시 강제 무효화 용도로만 운영
- 로그인 실패 응답은 **이메일/비밀번호 구분 없이 통일** (사용자 enumeration 차단)
- API Gateway에서 JWT 서명 검증 후 라우팅
- 키 로테이션: JWK `kid` 사용, 분기 1회 교체

## 서비스 간 통신 보안
- 내부 호출도 인증 필수 — Gateway 우회 차단
- **방식**: 내부 JWT (서비스 계정 토큰) 또는 mTLS
- 토이 단계: 내부 JWT (Gateway 가 사용자 JWT → 내부 토큰 변환)
- 운영 환경 가이드: mTLS — [docs/study/](../docs/study/) 추가 예정

## Spring Security
- Security Filter Chain은 서비스별 독립 구성
- CORS Origin 화이트리스트 명시 (`http://localhost:3000` 등) — 와일드카드 금지
- CSRF 비활성화 (Stateless REST API)
- 비밀번호: `BCryptPasswordEncoder`
  - 로컬/개발: cost 10
  - 운영: cost 12
- HTTPS 강제 — HTTP 접근 시 308 리다이렉트 또는 거부
- Spring Boot Actuator 엔드포인트는 `/actuator/health` 만 외부 노출, 나머지는 내부 망 또는 인증 필수

## 권한 (RBAC)
- 역할: `ROLE_USER`, `ROLE_ADMIN`
- 상품 등록/수정/삭제: ADMIN 전용
- 주문 조회/취소: 본인 주문만 접근 가능
- **IDOR 방어** — `path variable` 의 리소스 ID와 JWT subject 일치 검증 필수
- 권한 체크는 항상 서버에서 (UI 숨김 처리는 보안 아님)

## 비밀번호 정책
- 최소 8자, 영문 + 숫자 + 특수문자
- 사용자 정보(이메일·이름) 포함 금지
- 동일 문자 4회 이상 반복 금지
- 검증은 단일 Validator 클래스에서 일원화 (Bean Validation 어노테이션 → Service에서 같은 Validator 호출)

## 로그인 시도 제한 (Brute Force)
- IP + 이메일 조합으로 실패 횟수 카운트 (Redis)
- 5회 연속 실패 → 10분 일시 잠금
- 응답은 `429 Too Many Requests`
- Credential Stuffing(분산 IP) 대비: 이메일 단독 카운터도 병행, 임계 초과 시 CAPTCHA(향후)

## Rate Limiting
- API Gateway 레벨 (Spring Cloud Gateway + Redis Token Bucket)
- 인증: 분당 100req (userId 기준)
- 비인증: 분당 30req (IP 기준)
- 초과 시 `429`
- 상세 보류 메모: [docs/study/rate-limiting.md](../docs/study/rate-limiting.md)

## 응답 헤더 보안
- API JSON 응답에 CSP 적용은 효과 없음 — **정적 자산 서버(Frontend) 책임**
- API 서버 적용 항목:
  - `X-Content-Type-Options: nosniff`
  - `Strict-Transport-Security: max-age=31536000; includeSubDomains`
  - `Referrer-Policy: strict-origin`
- Frontend 정적 호스팅 측에 CSP, X-Frame-Options 적용
- 상세: [docs/study/response-header-security.md](../docs/study/response-header-security.md)

## 데이터 보안 (OWASP A02, A03)
- JPA 파라미터는 바인딩 변수 사용 (SQL Injection 차단)
- 사용자 입력은 Controller 진입 시 검증 (XSS, 길이, null)
- 비밀번호/토큰/키는 평문 저장·로그 출력 금지
- 응답 DTO 에서 민감 정보 제외 (Mass Assignment 방어 — Request DTO 와 Entity 절대 분리)
- 외부 URL 호출 시 SSRF 방어 — 화이트리스트 (OWASP A10)

## PII / 개인정보
- 주소·연락처·생년월일 등은 DB 저장 시 **암호화 (AES-256)**
- 응답·로그 출력 시 **마스킹** (`010-****-1234`)
- 보존 기간 정책 명시 (탈퇴 후 30일 후 파기 등)
- 감사 로그는 **append-only**, 위변조 방지

## 파일 업로드 (상품 이미지 등)
- MIME 타입 + 매직 바이트 검증 (확장자 신뢰 금지)
- 확장자 화이트리스트 (`jpg`, `png`, `webp` 만)
- 최대 크기 제한 (예: 5MB)
- 저장 경로는 사용자 입력 사용 금지 (path traversal 방어)
- 운영: S3 presigned URL + 안티바이러스 스캔

## 환경변수 / 시크릿 관리
- DB·JWT·Redis 접속정보는 `.env` 또는 환경변수
- `.env` 는 `.gitignore` 포함, 절대 커밋 금지
- `application.yml` 에 민감 정보 하드코딩 금지
- 운영: Vault / AWS Secrets Manager (학습 메모만)

## 의존성 관리 (OWASP A06)
- Gradle 의존성 명시적 버전 고정 (snapshot 금지)
- `OWASP Dependency Check` 정기 실행
- CVE 발생 시 즉시 업그레이드

## 보안 로깅 (OWASP A09)
- 로그인 성공/실패 모두 기록 (실패는 IP, 이메일 포함)
- 권한 거부(`403`) 발생 시 기록
- 관리자 행위(상품 등록/삭제 등) 감사 로그 — append-only
- 로그에 비밀번호·토큰·전체 카드번호 출력 금지

## OWASP Top 10 매핑
| OWASP | 대응 규칙 |
|-------|----------|
| A01 Broken Access Control | RBAC + IDOR 방어 + 본인 리소스 검증 |
| A02 Cryptographic Failures | BCrypt, JWT RS256, HTTPS 강제, PII 암호화 |
| A03 Injection | JPA 바인딩, 입력값 검증 |
| A04 Insecure Design | 위협 모델링, mTLS/내부 JWT |
| A05 Security Misconfiguration | Actuator 잠금, 에러 메시지 추상화 |
| A06 Vulnerable Components | 의존성 정기 점검 |
| A07 Auth Failures | Refresh Rotation, 시도 제한, enumeration 차단 |
| A08 Integrity Failures | Kafka 이벤트 멱등 처리, Schema Registry(향후) |
| A09 Logging Failures | append-only 감사 로그, 민감정보 마스킹 |
| A10 SSRF | 외부 URL 화이트리스트 |
