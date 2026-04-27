# Git 컨벤션

## 브랜치 종류
| 브랜치 | 용도 | 머지 대상 |
|--------|------|----------|
| `main` | 배포 가능한 안정 코드 (직접 push 금지) | — |
| `develop` | 통합 브랜치 (직접 push 금지) | `main` |
| `feature/*` | 새 기능 개발 | `develop` |
| `fix/*` | 버그 수정 | `develop` (긴급은 `main`) |
| `refactor/*` | 리팩토링 (기능 변경 없음) | `develop` |
| `test/*` | 테스트 추가/수정 | `develop` |
| `docs/*` | 문서 작업 | `develop` |
| `chore/*` | 빌드/설정/의존성 | `develop` |

## 브랜치 네이밍 규칙
- 형식: `<type>/<issue-번호>-<목적>`
- `type` 은 위 표의 prefix 와 동일
- `issue-번호` 는 GitHub Issue 번호, 없으면 생략
- `목적` 은 kebab-case, 영문 소문자, 핵심 명사 + 동사

예: `feature/12-auth-login`, `fix/order-stock-deadlock`

## 브랜치 삭제 규칙
- PR 머지 즉시 원격 브랜치 삭제 (GitHub PR 화면 "Delete branch" 버튼)
- 로컬 브랜치는 머지 후 본인이 정리: `git branch -d <브랜치>`
- 머지 안 된 브랜치를 삭제할 땐 `-D` (강제) — 작업 손실 주의
- 한 달 이상 비활성 브랜치는 정리 (분기마다 점검)

## 이슈 기반 워크플로우
- 새 작업은 GitHub Issue 먼저 생성 → 브랜치/PR 에 이슈 번호 연동
- PR 본문에 `Closes #N` → 머지 시 이슈 자동 종료
- 사소한 작업(오타 등)은 이슈 생략 가능

## 커밋 단위
- 한 커밋 = 한 가지 변경 (기능 + 리팩토링 섞지 말 것)
- 커밋만 봐도 의도 이해되어야 함
- WIP 커밋은 PR 머지 전 정리

## 커밋 메시지 형식
```
<type>: <subject>           ← 50자 이내, 명령형, 마침표 X

<body>                      ← 선택, "왜" 작성 (코드는 "무엇" 보여줌)

<footer>                    ← 선택, Closes #N / Co-Authored-By
```

`type` 은 브랜치 prefix 와 동일 (`feat`/`fix`/`refactor`/`test`/`docs`/`chore`/`perf`/`style`).

## 머지 방식
| 상황 | 방식 | 이유 |
|------|------|------|
| `feature/*` → `develop` | Squash and Merge | 기능 단위로 히스토리 정리 |
| `develop` → `main` | Merge Commit | 릴리즈 단위 보존 |
| 긴급 `fix/*` → `main` | Squash + cherry-pick to develop | 핫픽스 추적 |

- Rebase Merge 사용 안 함

## AI 도구 사용 시
- 커밋 footer 에 `Co-Authored-By: <도구명> <noreply@...>` 명시 (투명성)

## 브랜치 보호 규칙 (GitHub)
- `main`, `develop` 직접 push 금지
- PR 1개 이상 승인 필수 (혼자 작업이면 self-review)
- CI 통과 필수
- 머지 전 최신 develop 과 동기화 필수
