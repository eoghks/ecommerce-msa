# 보안 규칙

## 인증/인가
- JWT 기반 (Access + Refresh Token)
- 알고리즘: **RS256 (비대칭)** — 공개키만 Gateway/서비스에 배포, 개인키는 Auth Service만 보유
- Access Token 유효기간: 30분
- Refresh Token 유효기간: 7일, Redis 저장
- 로그아웃 시 Access Token → Redis 블랙리스트
- API Gateway에서 JWT 서명 검증 후 라우팅

## Spring Security
- Security Filter Chain은 서비스별 독립 구성
- CORS는 Gateway에서만 허용, 각 서비스는 Gateway만 신뢰
- CSRF 비활성화 (Stateless REST API)
- 비밀번호는 `BCryptPasswordEncoder` 로 암호화 (cost 12)

## 권한 (RBAC)
- 역할: `ROLE_USER`, `ROLE_ADMIN`
- 상품 등록/수정/삭제: ADMIN 전용
- 주문 조회/취소: 본인 주문만 접근 가능
- **권한 체크는 항상 서버에서 검증** (UI 숨김 처리는 보안 아님)

## 비밀번호 정책
- 최소 8자, 영문 + 숫자 + 특수문자 조합
- 사용자 정보(이메일, 이름) 포함 금지
- 동일 문자 4회 이상 반복 금지
- 정책 검증은 Request DTO Bean Validation + Service 레이어 이중 적용

## 로그인 시도 제한 (Brute Force 방어)
- IP + 이메일 조합으로 실패 횟수 카운트 (Redis)
- 5회 연속 실패 → 10분 일시 잠금
- 응답은 `429 Too Many Requests`
- 성공 시 카운터 초기화

## Rate Limiting
- API Gateway 레벨에서 적용 (Spring Cloud Gateway + Redis)
- 인증 사용자: 분당 100req
- 비인증 사용자: 분당 30req (IP 기준)
- 초과 시 `429 Too Many Requests`

## 데이터 보안 (OWASP A02, A03)
- JPA 파라미터는 바인딩 변수 사용 (문자열 직접 치환 금지 — SQL Injection)
- 사용자 입력값은 Controller 진입 시점에 검증 (XSS, 길이, null)
- 비밀번호/토큰/키는 평문 저장 및 로그 출력 금지
- 민감 정보는 응답 DTO에서 제외 (`@JsonIgnore` 또는 별도 Response DTO)
- 외부 URL 호출 시 SSRF 방어 — 화이트리스트 검증 (OWASP A10)

## 환경변수 / 시크릿 관리
- DB 접속정보, JWT 키, Redis 접속정보는 `.env` 파일 또는 환경변수
- `.env` 는 `.gitignore` 에 포함 — 절대 커밋 금지
- `application.yml` 에 민감 정보 하드코딩 금지
- 운영 환경은 Vault / AWS Secrets Manager 등 사용 (학습용으로 메모만)

## 의존성 관리 (OWASP A06)
- Gradle 의존성은 명시적 버전 고정 (snapshot 금지)
- `dependency-check-gradle` 또는 `OWASP Dependency Check` 정기 실행
- CVE 발생 시 즉시 업그레이드

## 보안 로깅 (OWASP A09)
- 로그인 성공/실패 모두 기록 (실패는 IP, 이메일 포함)
- 권한 거부(`403`) 발생 시 기록
- 관리자 행위(상품 등록/삭제 등) 감사 로그 기록
- 로그에 비밀번호·토큰·전체 카드번호 출력 금지

## OWASP Top 10 매핑
| OWASP | 대응 규칙 |
|-------|----------|
| A01 Broken Access Control | RBAC + 본인 리소스 검증 |
| A02 Cryptographic Failures | BCrypt(cost 12), JWT RS256, HTTPS |
| A03 Injection | JPA 바인딩, 입력값 검증 |
| A04 Insecure Design | 설계 단계 위협 모델링 |
| A05 Security Misconfiguration | Spring 기본값 검토, 에러 메시지 추상화 |
| A06 Vulnerable Components | 의존성 정기 점검 |
| A07 Auth Failures | 로그인 시도 제한, 강한 비밀번호 정책 |
| A08 Integrity Failures | Kafka 이벤트 멱등 처리, 서명 검증 |
| A09 Logging Failures | 보안 이벤트 기록, 민감정보 마스킹 |
| A10 SSRF | 외부 URL 화이트리스트 |
