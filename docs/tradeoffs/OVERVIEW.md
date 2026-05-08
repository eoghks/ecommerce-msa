# Tradeoffs Overview

포트폴리오 범위 또는 인프라 제약으로 의도적으로 선택한 트레이드오프 목록.
면접 질문 대비 및 향후 개선 참고용.

> **ID 심각도 코드**: CR(Critical) · HR(High) · MD(Medium) · LW(Low) — 코드 리뷰 기준 심각도. 뒤 숫자는 같은 등급 내 순서.

| ID | 주제 | 대상 서비스 | 스킵 이유 요약 | 문서 |
|----|------|------------|--------------|------|
| CR-01 | RSA 키 페어 인메모리 생성 | auth-service | 로컬 단일 인스턴스 환경 — 키 관리 인프라(Vault/K8s) 없이 PEM 주입 시 평문 노출 위험 | [CR-01-rsa-key-inmemory.md](./CR-01-rsa-key-inmemory.md) |
| CR-02 | Refresh Token Rotation TOCTOU | auth-service | 동시 요청 시나리오 없는 로컬 환경 — Redis Lua 구현 시 테스트 복잡도 급증 | [CR-02-refresh-toctou.md](./CR-02-refresh-toctou.md) |
| MD-03 | DB 자격증명 yml 기본값 노출 | auth-service | 로컬 전용 계정 — `.env` 추가 시 Docker Compose·Spring Boot 양쪽 설정 변경 필요 | [MD-03-db-credentials.md](./MD-03-db-credentials.md) |
| LW-04 | Role 관리 API 미구현 | auth-service | ADMIN이 USER → ADMIN 승격하는 API 없음 — 관리자 대시보드 구현 시 자연스럽게 추가 예정 | [LW-04-role-management.md](./LW-04-role-management.md) |
