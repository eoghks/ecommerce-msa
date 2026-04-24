# ecommerce-msa CLAUDE.md

## 프로젝트 개요
MSA 기반 이커머스 플랫폼 (이직용 포트폴리오)
- 스택: Spring Boot + JPA, React, Docker, Redis
- 구조: 모노레포 (서비스별 하위 디렉토리)

## 서비스 구조
```
ecommerce-msa/
├── gateway/          # Spring Cloud Gateway
├── auth-service/     # 회원가입/로그인/RBAC
├── product-service/  # 상품 CRUD + Redis 캐싱
├── order-service/    # 주문/재고 + Redis Lock
├── monitoring/       # Actuator 기반 대시보드
├── frontend/         # React
├── docker/           # Docker Compose
└── docs/             # 설계 문서
```

## Java 코딩 규칙
- `jakarta.*` 사용 (`javax.*` 금지)
- Lombok 적극 사용: `@Getter` `@Setter` `@Builder` `@Slf4j`
- Controller → Service → Dao 레이어 패턴 준수
- 한국어 주석 허용

### 레이어 책임 규칙
- **Controller**: 요청/응답 처리, 입력값 바인딩만 — 비즈니스 로직 금지, `ResponseEntity` 사용
- **Service**: 비즈니스 로직 전담, 트랜잭션 경계 관리
- **Dao**: DB 접근만 — MyBatis mapper 호출, SQL 실행만 허용
  - 파라미터 1개 → 그대로 전달 / 2개 이상 → 도메인 객체에 담아 전달 (`Object[]` 금지)

### 파일 생성 규칙
- `.java` `.jsx` `.js` `.xml` 파일 생성 후 즉시 `git add <파일경로>` 실행

### 테스트 코드 규칙
- 코드 작성 시 테스트 코드를 병렬로 작성
- 테스트 보고 형식: `통과 N건 / 실패 0건`

## 보안 규칙
- SQL 파라미터는 항상 `#{}` 바인딩 (`${}` 금지)
- 사용자 입력값은 Controller 진입 시점에 검증
- 비밀번호/토큰/키는 평문 저장 및 로그 출력 금지

## 브랜치 전략
- `main` — 배포 가능한 안정 브랜치
- `develop` — 통합 브랜치
- `feature/<기능명>` — 기능 개발 브랜치
- `fix/<버그명>` — 버그 수정 브랜치
- **신규/버그 작업 시 반드시 브랜치 생성 후 PR로 병합**

## 문서화 규칙
- 모든 작업 결과물은 `docs/` 폴더에 마크다운으로 저장
- 기술 선택 근거는 `docs/decisions/` 에 기록

## 토큰 절약 규칙
- 응답은 간결하게 — 묻지 않은 설명 금지
- 코드 출력 시 변경된 부분만 표시
- 병렬 처리 가능한 도구 호출은 단일 메시지로 묶어 실행
