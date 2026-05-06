# Auth Service 엔티티

## User

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | Long | PK, AUTO_INCREMENT | |
| email | VARCHAR(100) | UNIQUE, NOT NULL | 로그인 식별자 |
| password | VARCHAR(255) | NOT NULL | BCrypt 해시 저장 |
| name | VARCHAR(50) | NOT NULL | 표시 이름 |
| role | VARCHAR(20) | NOT NULL, DEFAULT 'USER' | USER \| ADMIN |
| createdAt | TIMESTAMP | NOT NULL | 가입 일시 |
| updatedAt | TIMESTAMP | NOT NULL | 수정 일시 |

### 인덱스
- `email` — UNIQUE 인덱스 (로그인 조회)

### 연관관계
- 없음 (Auth Service 자체적으로 완결)

---

## Redis — Refresh Token

실제 테이블이 아닌 Redis Key-Value 저장.

| Key | Value | TTL |
|-----|-------|-----|
| `auth:refresh:{userId}` | Refresh Token (String) | 7일 |

### 정책
- 로그인 시 신규 발급 → 기존 토큰 덮어쓰기 (단일 세션)
- `/auth/refresh` 호출 시 Rotation — 기존 토큰 삭제 + 신규 토큰 저장
- `/auth/logout` 호출 시 해당 키 삭제 → 토큰 무효화
- 만료된 키는 Redis TTL이 자동 삭제

### Reuse Detection [운영]
- 이미 사용된 Refresh Token 재사용 감지 → 전체 세션 강제 무효화
- 토이 단계 미적용
