# 운영 / 빌드 / 배포 규칙

## 환경 분리
- `local` — 로컬 개발 (Docker Compose)
- `dev` — 통합 개발 환경
- `prod` — 운영 (배포 안 하지만 시뮬레이션)
- 설정 파일: `application.yml` (공통) + `application-{env}.yml` (환경별)
- 활성 프로파일: `SPRING_PROFILES_ACTIVE` 환경변수

## 시크릿 / 환경변수
- 로컬: `.env` 파일 (gitignore)
- CI/배포: GitHub Actions Secrets, 향후 Vault/AWS Secrets Manager 검토
- 코드에 하드코딩 금지

## Gradle 멀티모듈
- 루트 `settings.gradle` 에 서비스별 모듈 등록
- 공통 `build.gradle` (의존성 버전 관리는 `libs.versions.toml`)
- `dependency:` 표기 통일, snapshot 금지

## Docker / 이미지
- 각 서비스 자체 `Dockerfile` (multi-stage build)
- 이미지 태깅:
  - 개발: `<service>:dev-<gitsha>`
  - 릴리즈: `<service>:v<MAJOR>.<MINOR>.<PATCH>` + `<service>:latest`
- `.dockerignore` 필수 (빌드 컨텍스트 최소화)
- 이미지 사이즈 200MB 이하 목표 (alpine + JRE 21)

## CI/CD [토이 필수]
- **도구**: GitHub Actions (무료, 학습 자료 풍부)
- **단계별 학습**: 상세는 [docs/study/cicd.md](../docs/study/cicd.md) 참고
- **토이 단계 최소 구성** (Week 6 까지 도달 목표):
  1. PR 생성 시 빌드 + 단위 테스트 자동 실행
  2. main/develop 머지 시 Docker 이미지 빌드
  3. 커버리지 리포트 PR 코멘트 (선택)
- **path filter** — 변경된 서비스만 빌드 (모노레포 핵심)
- 향후: 통합 테스트(Testcontainers), 배포 자동화

## 모니터링 / 관측성
- **메트릭**: Spring Actuator + Micrometer + Prometheus 노출 (`/actuator/prometheus`)
- **로그**: 구조화 로그 (JSON), MDC 에 `requestId`/`correlationId` 포함
- **분산 추적** (선택): OpenTelemetry → Jaeger/Zipkin
- **헬스체크**: `/actuator/health` 만 외부 노출, 나머지는 인증

## SLO / 알람 (학습 목표)
- 핵심 API 응답 시간: p95 < 500ms, p99 < 1s
- 에러율: 5xx < 1%
- Saga 보상 트랜잭션 성공률: 99%
- 알람: Prometheus Alertmanager (학습용, 슬랙 웹훅)

## 백업 / 마이그레이션
- Flyway 마이그레이션은 항상 forward (rollback 스크립트 별도)
- 운영 DB 백업: 일 1회 (학습 영역, 실제 미적용)
- 마이그레이션 실패 시: 신규 forward 마이그레이션으로 보정 (downward 지양)

## API 문서화
- **springdoc-openapi** 로 OpenAPI 3.0 자동 생성
- 각 서비스 `/swagger-ui` 노출 (개발 환경만)
- API 변경 시 `docs/api-spec.md` 동시 업데이트

## 빌드 캐싱 / 속도
- Gradle Build Cache 활성화
- GitHub Actions: dependency cache (`~/.gradle/caches`)
- Docker layer 캐시 (`docker/build-push-action` `cache-from`)

## 토이 단계에서 생략하는 것 [운영]
- Auto Scaling, Load Balancer 구성
- Vault / Secrets Manager 실제 연동
- 멀티 리전, DR
- Service Mesh (Istio, Linkerd)
- 카오스 엔지니어링
- 보안 스캐너 자동화 (SonarQube, Snyk)

## 학습 자료
- [docs/study/cicd.md](../docs/study/cicd.md) — GitHub Actions 단계별 학습
