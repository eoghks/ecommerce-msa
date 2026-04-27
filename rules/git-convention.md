# Git 컨벤션

## 브랜치 전략

```
main        ← 배포 가능한 안정 브랜치 (직접 push 금지)
develop     ← 통합 브랜치 (PR로만 머지)
feature/*   ← 기능 개발
fix/*       ← 버그 수정
docs/*      ← 문서 작업
chore/*     ← 설정, 빌드, 의존성 변경
refactor/*  ← 리팩토링
test/*      ← 테스트 추가/수정
```

- **신규 기능 / 버그 수정 시 반드시 브랜치 생성 후 PR로 병합**
- `main` 직접 push 금지
- 브랜치 완료 후 머지하면 해당 브랜치 삭제

## 브랜치 네이밍
- 형식: `<type>/<issue-번호>-<짧은-설명-kebab-case>`
- 이슈가 있는 경우 번호 포함, 없으면 생략 가능

```
feature/12-auth-login
feature/15-product-redis-cache
fix/23-order-stock-deadlock
docs/api-spec-update
chore/docker-compose-setup
```

## 이슈 기반 워크플로우
- 새 작업은 GitHub Issue 먼저 생성 → 브랜치/커밋/PR 에 이슈 번호 연동
- PR 본문에 `Closes #12` 명시 → 머지 시 이슈 자동 종료
- 사소한 작업(오타 수정 등)은 이슈 생략 가능

## 커밋 단위 원칙
- **한 커밋 = 한 가지 변경** (기능 추가 + 리팩토링 섞지 않기)
- 커밋만 봐도 변경 의도가 이해되어야 함
- WIP 커밋은 PR 머지 전 squash 또는 정리

## 커밋 메시지 형식

```
<type>: <subject>      ← 50자 이내, 한국어 가능

<body>                 ← 선택, 무엇을·왜·영향 범위 (72자 줄바꿈)

<footer>               ← 선택, 이슈 참조 / Co-Authored-By
```

### 예시

```
feat: 로그인 JWT 발급 구현

이메일/비밀번호 검증 후 RS256 알고리즘으로 Access Token 발급.
Refresh Token 은 Redis 에 저장하고 Access 만료 시 재발급에 사용.

영향 범위: AuthService, JwtTokenProvider, RedisConfig
Closes #12
Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```

## 커밋 메시지 작성 규칙
- subject 는 명령형 (적용 후 상태 X, "구현 X", "구현" O)
- subject 끝에 마침표 금지
- body 는 **무엇이 아닌 왜** 작성 (코드는 무엇을 보여줌)
- AI 도구 사용 시 `Co-Authored-By` 명시 (투명성)

## 타입 정의
| 타입 | 설명 |
|------|------|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `docs` | 문서 변경 |
| `chore` | 빌드/설정/의존성 |
| `refactor` | 리팩토링 (기능 변경 없음) |
| `test` | 테스트 추가/수정 |
| `style` | 포맷, 세미콜론 등 코드 변경 없음 |
| `perf` | 성능 개선 |

## 머지 방식
| 상황 | 방식 | 이유 |
|------|------|------|
| `feature/*` → `develop` | **Squash and Merge** | 기능 단위로 히스토리 정리, 머지 후 develop 깔끔 |
| `develop` → `main` | **Merge Commit** | 릴리즈 단위 보존, 어느 기능이 언제 배포됐는지 추적 |
| `fix/*` (긴급) → `main` | **Squash and Merge** + `develop` 으로 cherry-pick | 핫픽스 추적 |

- Rebase Merge 는 사용 안 함 (히스토리 변조 위험)
- Squash 시 PR 제목 = 최종 커밋 메시지가 됨 → PR 제목도 컨벤션 준수

## 보호 규칙 (GitHub Branch Protection)
- `main`, `develop` 직접 push 금지
- PR 1개 이상 승인 필수 (혼자 작업이면 self-review)
- 모든 CI 통과 필수
- 머지 전 최신 develop 과 동기화 필수
