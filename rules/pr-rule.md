# PR 규칙

## 기본 원칙
- 모든 변경은 PR을 통해서만 `develop` 에 머지
- `main` 직접 push 금지 — `develop` → `main` PR로만 머지
- PR은 한 가지 작업 단위로 작게 유지
- **변경량 500줄 초과 시 분리 권장** (자동 생성 코드는 예외)

## PR 제목
- 형식: `<type>(<scope>): <subject>` (Conventional Commits 준수, [git-convention.md](git-convention.md) 참조)
- 예: `feat(auth): 로그인 JWT 발급 구현`
- Squash 머지 시 PR 제목이 그대로 커밋 메시지가 됨 → 컨벤션 필수

## PR 본문 템플릿
```markdown
## 작업 내용
- 변경한 내용 요약 (왜)

## 주요 변경
- 핵심 클래스/모듈 단위로 정리

## 테스트
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과 (해당 시)
- [ ] 로컬 동작 확인

## Self Review
- [ ] 본인 코드 한 번 더 검토
- [ ] AI 작성 코드 라인 단위 재검토 (해당 시)
- [ ] 디버그 코드/불필요한 주석 제거

## Breaking Change
- 없음 / (있다면 영향 범위와 마이그레이션 가이드)

## 스크린샷
- UI 변경 시 첨부 (Before/After)

## 참고
Closes #N
관련 문서: docs/decisions/...
```

## 머지 전 체크리스트
- [ ] 브랜치명·커밋 메시지가 컨벤션 준수
- [ ] 모든 테스트 통과 (CI 포함)
- [ ] 최신 develop 과 동기화 완료
- [ ] 민감 정보(`.env`, 시크릿) 미포함
- [ ] 디버그 코드/주석 처리 코드 없음
- [ ] 관련 문서 (docs/, rules/) 동시 업데이트 완료
- [ ] AI 사용 시 `Co-Authored-By` 명시 + 검증 절차 수행
