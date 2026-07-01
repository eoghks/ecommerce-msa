# CI/CD 학습 메모 (GitHub Actions)

## 상태
- **현재**: 미구현 (Week 6 목표)
- **사유**: CI/CD 처음 다루는 단계, 학습 후 점진 적용
- **목표**: Week 6 까지 최소 구성 적용 가능 수준

---

## CI/CD 가 뭐냐 (개념)

| 용어 | 의미 |
|------|------|
| **CI** (Continuous Integration) | 코드 변경마다 자동으로 빌드 + 테스트 실행 → 깨진 코드 조기 발견 |
| **CD** (Continuous Delivery) | 빌드된 결과물을 배포 가능한 상태로 자동 준비 (수동 배포 트리거) |
| **CD** (Continuous Deployment) | 위 + 자동 배포까지 |
| **파이프라인** | CI/CD 단계들의 순서 정의 (yml 파일) |

토이 단계 목표: **CI 최소 구성** + **이미지 빌드 자동화** 까지.

---

## GitHub Actions 핵심 개념

| 용어 | 설명 |
|------|------|
| Workflow | `.github/workflows/*.yml` 파일 단위 |
| Event | trigger (push, pull_request, schedule 등) |
| Job | 같은 환경에서 실행되는 step 묶음 |
| Step | 명령어 또는 액션 호출 |
| Action | 재사용 가능한 작업 단위 (`actions/checkout@v4` 등) |
| Runner | 실행 환경 (`ubuntu-latest` 무료) |

---

## 단계별 학습 로드맵

### Step 1 — Hello World CI (1시간)
PR 생성 시 "Hello" 출력하는 워크플로 만들기

```yaml
# .github/workflows/hello.yml
name: Hello CI
on: [pull_request]
jobs:
  hello:
    runs-on: ubuntu-latest
    steps:
      - run: echo "Hello CI"
```

PR 만들어서 GitHub Actions 탭에서 결과 확인.

### Step 2 — Java 빌드 + 단위 테스트 (2시간)
```yaml
name: Build & Test
on:
  pull_request:
    branches: [develop, main]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - uses: gradle/actions/setup-gradle@v3
      - run: ./gradlew test
```

배울 것: `actions/checkout`, `setup-java`, Gradle 캐시.

### Step 3 — Path Filter (2시간)
모노레포에서 변경된 서비스만 빌드.

```yaml
on:
  pull_request:
    paths:
      - 'auth-service/**'
      - 'common/**'
```

또는 `dorny/paths-filter` 액션으로 동적 분기.

### Step 4 — 커버리지 리포트 (선택, 2시간)
JaCoCo 결과를 PR 코멘트로:
```yaml
- uses: madrapps/jacoco-report@v1.6
  with:
    paths: '**/build/reports/jacoco/test/jacocoTestReport.xml'
    token: ${{ secrets.GITHUB_TOKEN }}
    min-coverage-overall: 60
```

### Step 5 — Docker 이미지 빌드 (3시간)
develop 머지 시 이미지 빌드 + GHCR 푸시.

```yaml
on:
  push:
    branches: [develop]
jobs:
  docker:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - uses: docker/build-push-action@v5
        with:
          push: true
          tags: ghcr.io/eoghks/auth-service:dev-${{ github.sha }}
```

### Step 6 — 통합 테스트 (선택, Week 6 이후)
Testcontainers + 별도 job 분리.

---

## 자주 쓰는 액션 모음

| 액션 | 용도 |
|------|------|
| `actions/checkout@v4` | 코드 체크아웃 |
| `actions/setup-java@v4` | JDK 설치 |
| `gradle/actions/setup-gradle@v3` | Gradle 설치 + 캐시 |
| `actions/cache@v4` | 임의 디렉터리 캐시 |
| `docker/setup-buildx-action@v3` | Docker buildx |
| `docker/login-action@v3` | 레지스트리 로그인 |
| `docker/build-push-action@v5` | 이미지 빌드 + push |

---

## 자주 빠지는 함정

- **Secrets** — 코드에 토큰 하드코딩 금지, `secrets.<NAME>` 사용
- **`pull_request_target`** vs **`pull_request`** — 외부 PR 보안 차이 (대부분 후자 사용)
- **Concurrency** — 같은 브랜치 push 연달아 시 이전 워크플로 취소: `concurrency: { group: ${{ github.ref }}, cancel-in-progress: true }`
- **캐시 키** — Gradle/Maven 의존성 변경 시 키 변경 안 하면 캐시 오염
- **Runner 시간 제한** — 무료 6시간/job, 분당 빌링 (private repo)

---

## 토이 프로젝트 적용 우선순위

| 우선 | 항목 | 시간 |
|------|------|------|
| 1 | PR 시 빌드+테스트 (Step 2) | 2h |
| 2 | Path Filter (Step 3) | 2h |
| 3 | 커버리지 리포트 (Step 4) | 2h |
| 4 | Docker 이미지 자동 빌드 (Step 5) | 3h |
| 5 | 통합 테스트 (Step 6) | 4h+ |

**최소 목표**: 우선 1~2 만 적용해도 면접에선 충분히 어필 가능.

---

## 면접 답변 시나리오
> Q: CI/CD 어떻게 구성했나요?
> A: GitHub Actions로 PR 시 빌드·테스트, develop 머지 시 Docker 이미지 빌드까지 자동화했습니다. 모노레포라 path filter 로 변경된 서비스만 빌드되게 했고, 통합 테스트(Testcontainers)는 별도 job 으로 분리해 빠른 피드백 + 신뢰성 균형을 잡았습니다.

---

## 학습 자료
- [GitHub Actions 공식 문서](https://docs.github.com/actions)
- [Awesome Actions](https://github.com/sdras/awesome-actions)
- [GitHub Actions 한국어 입문 (인프런 무료 강의 등)](https://www.inflearn.com)
