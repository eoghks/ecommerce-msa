# Git 컨벤션

## 브랜치 전략

```
main        ← 배포 가능한 안정 브랜치 (직접 push 금지)
develop     ← 통합 브랜치 (PR로만 머지)
feature/*   ← 기능 개발
fix/*       ← 버그 수정
docs/*      ← 문서 작업
chore/*     ← 설정, 빌드, 의존성 변경
```

- **신규 기능 / 버그 수정 시 반드시 브랜치 생성 후 PR로 병합**
- `main` 직접 push 금지
- 브랜치 완료 후 머지하면 해당 브랜치 삭제

## 브랜치 네이밍

```
feature/auth-login
feature/product-redis-cache
fix/order-stock-deadlock
docs/api-spec-update
chore/docker-compose-setup
```

## 커밋 메시지 규칙

```
<type>: <내용> (한국어 가능)

예시:
feat: 로그인 JWT 발급 구현
fix: 재고 차감 동시성 오류 수정
docs: API 명세 업데이트
chore: Docker Compose PostgreSQL 추가
refactor: OrderService 주문 생성 로직 분리
test: AuthService 로그인 단위 테스트 추가
```

## 타입 정의
| 타입 | 설명 |
|------|------|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `docs` | 문서 변경 |
| `chore` | 빌드/설정/의존성 |
| `refactor` | 리팩토링 |
| `test` | 테스트 추가/수정 |
| `style` | 포맷, 세미콜론 등 코드 변경 없음 |
