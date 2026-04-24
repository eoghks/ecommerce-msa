# 개발 로드맵

> 하루 2시간 기준, 전체 7주

## Week 0 — 규칙·문서화 세팅 + GitHub 초기화
- [x] 프로젝트 디렉토리 생성
- [x] CLAUDE.md 작성
- [x] docs/ 폴더 구조 설계
- [ ] GitHub 레포 생성 및 원격 연결
- [ ] .gitignore 작성
- [ ] README 초안

## Week 1 — 인프라 세팅
- [ ] Docker Compose (MySQL, Redis)
- [ ] Spring Cloud Gateway
- [ ] 공통 모듈 (응답 포맷, 예외 처리)
- [ ] AI Agent 환경 구성 (리팩토링·품질·보안 Agent)

## Week 2 — Auth Service
- [ ] 회원가입 / 로그인 (JWT 발급)
- [ ] Redis 세션 저장
- [ ] RBAC 권한 구조 (USER / ADMIN)

## Week 3 — Product Service
- [ ] 상품 CRUD (JPA)
- [ ] Redis 상품 목록 캐싱 (TTL)
- [ ] 카테고리, 검색 기능

## Week 4 — Order Service
- [ ] 주문 생성, 상태 관리
- [ ] 재고 차감 + Redis Lock 동시성 처리
- [ ] 주문 내역 조회

## Week 5 — React 프론트엔드
- [ ] 쇼핑몰 메인, 상품 목록/상세
- [ ] 장바구니, 주문하기 화면
- [ ] 관리자 화면 (상품/주문 관리)

## Week 6 — Monitoring + 마무리
- [ ] Spring Actuator 기반 서비스 상태 대시보드
- [ ] 통합 테스트, README 완성
