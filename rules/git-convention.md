# Git 컨벤션

## 브랜치 전략 — GitFlow Lite
1인 토이프로젝트에서도 `develop` 을 유지하는 이유:
- 면접 어필 — 다중 환경 배포(dev/stage/prod) 운영 경험 시뮬레이션
- 릴리즈 단위 추적 — `develop` 통합 후 `main` 으로 묶어서 SemVer 태깅
- 핫픽스 분리 — `main` 직접 패치 + `develop` 동기화 패턴 학습

## 브랜치 종류
| 브랜치 | 용도 | 머지 대상 |
|--------|------|----------|
| `main` | 배포 가능한 안정 코드 (직접 push 금지) | — |
| `develop` | 통합 브랜치 (직접 push 금지) | `main` |
| `feature/*` | 새 기능 개발 | `develop` |
| `fix/*` | 일반 버그 수정 | `develop` |
| `hotfix/*` | 운영 긴급 패치 | `main` + `develop` 양쪽 머지 |
| `chore/*` | 빌드/설정/의존성/리팩토링/테스트/문서 등 비기능 변경 | `develop` |

> 8종 → 5종으로 정리. `refactor`, `test`, `docs`, `perf`, `style` 은 모두 `chore/*` 로 통합.

## 브랜치 네이밍 규칙
- 형식: `<type>/<issue-번호>-<목적>`
- `type` 은 위 표의 prefix (단수형: `feature`, `fix`, `hotfix`, `chore`)
- `issue-번호` 는 GitHub Issue 번호, 없으면 생략
- `목적` 은 kebab-case, 영문 소문자

예: `feature/12-auth-login`

## 브랜치 삭제 규칙
- PR 머지 즉시 원격 브랜치 삭제 (GitHub PR 화면 "Delete branch")
- 로컬 브랜치는 머지 후 정리: `git branch -d <브랜치>`
- 머지 안 된 브랜치 삭제는 `-D` 강제 (작업 손실 주의)

## 충돌 해결
- feature 작업 중 develop 동기화: `git pull --rebase origin develop`
- 충돌 시 IDE 머지 도구 활용, 해결 후 `git rebase --continue`
- 이미 push 한 브랜치 rebase 후엔 `git push --force-with-lease` (단독 `--force` 금지)

## 이슈 기반 워크플로우
- 새 작업은 GitHub Issue 먼저 생성 → 브랜치/PR 에 이슈 번호 연동
- PR 본문에 `Closes #N` → 머지 시 이슈 자동 종료
- 사소한 작업(오타 등)은 이슈 생략 가능

## feature → develop 머지 타이밍
- 기능 완성(커밋 + 테스트 통과) 즉시 `develop` 머지
- 미완성 상태로 feature 브랜치를 장기 유지 금지
- 머지 순서: feature 커밋 완료 → develop 머지 → feature 브랜치 삭제

## 커밋 단위
- 한 커밋 = 한 가지 변경
- 커밋만 봐도 의도 이해되어야 함
- WIP 커밋은 PR 머지 전 정리

## 커밋 메시지 형식 (Conventional Commits)
```
<type>(<scope>): <subject>      ← 50자 이내, 명령형, 마침표 X

<body>                          ← 선택, "왜" 작성

<footer>                        ← Closes #N / BREAKING CHANGE / Co-Authored-By
```

### scope (모노레포 필수)
어느 서비스/모듈 변경인지 표기.
- `gateway`, `auth`, `product`, `order`, `monitoring`, `frontend`
- 인프라/공통: `infra`, `common`, `docs`, `ci`

예시:
```
feat(auth): 로그인 JWT 발급 구현
fix(order): 재고 차감 동시성 오류 수정
chore(infra): Docker Compose Kafka 추가
```

### type
| type | 설명 |
|------|------|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 (기능 변경 없음) |
| `test` | 테스트 추가/수정 |
| `docs` | 문서 |
| `chore` | 빌드/설정/의존성 |
| `perf` | 성능 개선 |
| `style` | 포맷·세미콜론 등 코드 변경 없음 |
| `revert` | 이전 커밋 되돌리기 |

> 브랜치는 5종(`feature`/`fix`/`hotfix`/`chore`/`main`/`develop`), 커밋 type 은 변경 의도 단위로 더 세분 — 다른 차원이라 분리.
>
> 브랜치 ↔ 커밋 type 매핑:
> - `feature/*` → `feat`
> - `fix/*` / `hotfix/*` → `fix`
> - `chore/*` → `chore` / `refactor` / `test` / `docs` / `perf` / `style` / `revert` (모두 비기능 변경)

### Breaking Change
호환성 깨지는 변경:
- subject 끝에 `!`: `feat(auth)!: JWT 클레임 구조 변경`
- 또는 footer: `BREAKING CHANGE: <설명>`
- SemVer MAJOR 증가 트리거

## AI 도구 사용 시
- 커밋 footer 에 `Co-Authored-By: <도구명> <noreply@...>` 명시
- AI 작성 코드 검증 절차:
  1. 작성 즉시 본인이 라인 단위 리뷰
  2. 실행 + 테스트로 동작 확인
  3. PR 셀프 리뷰 시 "AI 작성 부분 재검토" 체크리스트 항목

## 머지 방식
| 상황 | 방식 | 이유 |
|------|------|------|
| `feature/*`·`fix/*`·`chore/*` → `develop` | Squash and Merge | 기능 단위로 히스토리 정리 |
| `develop` → `main` | Merge Commit | 릴리즈 단위 보존 |
| `hotfix/*` → `main` | Merge Commit | 핫픽스 추적 |
| `hotfix/*` → `develop` | Merge Commit (또는 동일 브랜치 추가 머지) | 양쪽 동기화 (cherry-pick 비권장 — squash 후 해시 변경 시 충돌) |

- Rebase Merge 사용 안 함 — 커밋 해시 변조, PR 단위 추적 어려움

## 릴리즈 / 태그
- `develop` → `main` 머지 후 태그: `v<MAJOR>.<MINOR>.<PATCH>` (Semantic Versioning)
  - `MAJOR`: 호환성 깨지는 변경 (`BREAKING CHANGE` 포함된 경우)
  - `MINOR`: 하위 호환 새 기능 (`feat`)
  - `PATCH`: 버그 수정 (`fix`)
- 태그 생성 시 GitHub Release 작성 (변경 요약 + 이슈 링크)
- (향후) `release-please` 또는 `semantic-release` 도입 검토 — 자동 버저닝·CHANGELOG

## 모노레포 버저닝 정책
- 단일 통합 버전 사용 (전체 서비스 공통 `v1.x.x`) — 토이 단계
- 운영 시 서비스별 독립 버저닝 검토 (`auth-service@1.2.0`, `order-service@2.0.0`)

## 브랜치 보호 규칙 (GitHub)
- `main`, `develop` 직접 push 금지
- 머지 전 CI 통과 필수
- 머지 전 최신 develop 과 동기화 필수
