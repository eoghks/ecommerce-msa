# PR 규칙

## 기본 원칙
- 모든 변경은 PR 통해 `develop` 머지, `develop` → `main` 도 PR
- `main` 직접 push 금지
- PR 한 가지 작업 단위로 작게 유지
- 변경량 권장 300줄 / 하드 리밋 600줄 (자동 생성 코드 예외)
- 작업 중인 PR 은 **Draft** 로 올려 가시성 확보

## PR 제목 (Conventional Commits)
- 형식: `<type>(<scope>): <subject>` ([git-convention.md](git-convention.md))
- 예: `feat(auth): 로그인 JWT 발급 구현`
- Squash 머지 시 PR 제목 = 커밋 메시지

## PR 본문 템플릿
```markdown
## 작업 내용
- 변경 의도와 핵심 요약 (왜)

## 영향 서비스
- `auth` / `product` / `order` / `gateway` / `frontend` / `infra`

## 주요 변경
- 핵심 클래스/모듈 단위

## API / 이벤트 스키마 변경
- 없음 / 있음
  - 변경 내용
  - 영향받는 컨슈머
  - 마이그레이션 가이드

## 테스트
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과 (해당 시)
- [ ] 계약 테스트 통과 (스키마 변경 시) [운영]
- [ ] 로컬 동작 확인

## Self Review
- [ ] 본인 코드 1차 검토
- [ ] AI 작성 부분 라인 단위 재검토 (해당 시)
- [ ] 디버그 코드/임시 주석 제거

## 커버리지 변화
- 이전 N% → 이후 M%

## Breaking Change
- 있음 → 영향 범위, 마이그레이션 가이드 명시 (없으면 섹션 생략)

## 스크린샷
- UI 변경 시만 첨부

## 참고
Closes #N
ADR: docs/decisions/ADR-NNN-...
```

> 테스트 동시 작성 원칙은 [testing-rule.md](testing-rule.md) 단일 출처. PR 체크박스는 결과 확인용.

## 라벨 정책
- 타입: `type:feat` / `type:fix` / `type:chore` / `type:docs`
- 영향 서비스: `service:auth` / `service:product` / `service:order` / `service:frontend` / `service:infra`
- 상태: `wip` / `needs-review`
- 특수: `breaking-change`

## CI 게이트 (필수 통과)
- 빌드 성공
- 린트 통과 (Checkstyle / ESLint)
- 단위 + 슬라이스 테스트 통과
- 커버리지 임계 충족 (핵심 도메인 80% 브랜치)
- 영향받는 서비스만 빌드/테스트 (path filter)
- 시크릿 스캔 통과

> CI 구성은 [ops-rule.md](ops-rule.md) + [docs/study/cicd.md](../docs/study/cicd.md)

## 머지 전 체크리스트
- [ ] 브랜치명·커밋 메시지 컨벤션 준수
- [ ] 모든 CI 통과
- [ ] 최신 develop 동기화
- [ ] 민감 정보 미포함
- [ ] 디버그/주석 처리 코드 제거
- [ ] 관련 문서(docs·rules) 동기화
- [ ] AI 사용 시 `Co-Authored-By` 명시 + 검증 완료
- [ ] 호환성 깨지는 변경 시 `breaking-change` 라벨 + ADR 첨부

## 선택 (포트폴리오 어필)
- PR 사이즈 / 리드타임 자가 측정 (DORA 지표 인지)
- CodeRabbit / Copilot Review 등 AI 리뷰 1차 활용
