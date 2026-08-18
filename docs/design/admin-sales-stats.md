# 설계 — 관리자 매출 통계 대시보드 (V1.1-7)

## 1. 목표 / 범위
관리자가 기간별 매출·주문 지표와 상위 상품·판매자를 한 화면에서 확인한다.

- 포함: 기간별 요약(매출·주문수·취소율·평균주문금액), 일별 추이, 상품별 Top N, 판매자별 Top N, 실패주문 추이
- 제외(백로그): 실시간 갱신, 코호트/재구매율, 광고·유입 분석, 엑셀 내보내기, 일별 집계 배치 테이블(초기엔 온디맨드 집계)

## 2. 배치 결정 — order-service
매출·주문 데이터가 전부 order-service에 있다(orders, order_item, failed_order_log). 집계를 order-service에서 수행하고 ADMIN 전용 API로 노출한다. **크로스서비스 호출 없음**(상품명·판매자ID는 주문 시점 스냅샷이 order_item에 있음).

| 지표 소스 | 위치 |
|---|---|
| 매출·주문수·취소율 | `orders` (status, total_price, created_at) |
| 상품별·판매자별 | `order_item` (product_id, product_name, price, quantity, seller_id, status) |
| 실패주문 추이 | `failed_order_log` (occurred_at) |

## 3. 매출 정의 (중요 — 계산 기준 확정)
취소가 섞이면 매출이 왜곡되므로 **유효 매출** 기준을 명확히 한다.

- **유효 매출** = 대상 주문의 **ACTIVE 항목만** `price × quantity` 합계
  - 근거: 주문 `total_price`는 생성 시점 금액이라 부분취소를 반영하지 않는다. 항목 단위 취소·반품 승인 시 항목이 CANCELLED가 되므로, ACTIVE 항목 합계가 실제 매출이다.
- **집계 대상 주문 상태**: `CONFIRMED`, `PARTIALLY_CANCELLED` (재고 차감이 실제로 일어난 주문)
  - `PENDING`(미확정) 제외, `CANCELLED`(전체취소) 제외 — 구매 인증(리뷰) 판정과 동일한 기준으로 일관성 유지
- **주문수** = 위 상태 주문의 건수
- **취소율** = `취소된 주문수 / 전체 주문수`
  - 전체 = 기간 내 생성된 모든 주문(PENDING 제외), 취소 = `CANCELLED` + `PARTIALLY_CANCELLED`
  - 부분취소를 취소로 셀지 논란 있어 **두 값을 분리 제공**: `fullyCancelledCount`, `partiallyCancelledCount`
- **평균 주문금액(AOV)** = 유효 매출 / 주문수
- 기간 기준 컬럼: `orders.created_at` (주문 생성일 기준)

## 4. API (order-service, ADMIN 전용)
기간은 `from`/`to`(ISO date, `to` 포함). 미지정 시 최근 30일. `from > to`면 400. 최대 조회 기간 **366일**(초과 400).

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/v1/admin/stats/summary?from&to` | 요약 지표 |
| GET | `/api/v1/admin/stats/daily?from&to` | 일별 추이(날짜·매출·주문수) |
| GET | `/api/v1/admin/stats/products?from&to&limit` | 상품별 Top N(기본 10, 최대 50) |
| GET | `/api/v1/admin/stats/sellers?from&to&limit` | 판매자별 Top N(기본 10, 최대 50) |
| GET | `/api/v1/admin/stats/failed-orders?from&to` | 실패주문 일별 추이 |

- 전부 `@PreAuthorize("hasRole('ADMIN')")`. SELLER/USER → 403. 미인증 → 401.
- 게이트웨이 `/api/v1/admin/**` 인증 필수 경로 확인(기존 패턴 따름).

### 응답 예시(요약)
```json
{
  "from": "2026-07-01", "to": "2026-07-31",
  "totalRevenue": 12450000,
  "orderCount": 87,
  "averageOrderValue": 143103,
  "fullyCancelledCount": 5,
  "partiallyCancelledCount": 3,
  "cancelRate": 0.0842,
  "failedOrderCount": 2
}
```
- 금액은 `long`(원 단위), 비율은 소수 4자리.
- 데이터 없는 기간: 0으로 채운 응답(null 금지). 일별 추이는 **빈 날짜도 0으로 채워** 그래프 끊김 방지.

## 5. 쿼리 / 성능
- JPQL 또는 QueryDSL **집계 쿼리**로 DB에서 계산(애플리케이션 루프 집계 금지). 파라미터 바인딩 필수(`${}` 금지).
- 일별 추이는 `date_trunc('day', created_at)` 그룹핑(PostgreSQL). 빈 날짜 채움은 애플리케이션에서 처리.
- 인덱스: `orders(created_at)`, `order_item(order_id)` 존재 여부 확인 후 없으면 Flyway로 추가(`orders.created_at` 인덱스 유력).
- **캐싱**: 초기엔 미적용(관리자 소수 사용). 느려지면 Redis 단기 캐시(예 5분) 또는 일별 집계 테이블로 확장 — 트레이드오프 문서화.
- N+1 금지: 집계는 단일 쿼리로 스칼라/DTO projection 반환.

## 6. 프론트 (TypeScript)
- 라우트 `/admin/stats` + Navbar **ADMIN 전용** 링크(`AdminOnlyRoute` 사용 — SELLER 진입 차단).
- 화면 구성:
  - 기간 선택(빠른 선택: 7일/30일/90일 + 직접 지정)
  - **KPI 카드 4개**: 유효 매출 / 주문수 / 평균 주문금액 / 취소율
  - **일별 추이 차트**(매출·주문수) — 차트 라이브러리 미도입 상태면 **경량 SVG 막대/라인 자체 구현**(의존성 추가 최소화)
  - **상품 Top N · 판매자 Top N 테이블**
  - 실패주문 추이(간단 표 또는 라인)
- 상태: 로딩 스켈레톤, 빈 상태("해당 기간 데이터가 없습니다"), 에러 시 `detail` 메시지.
- 반응형: 375에서 KPI 카드 1열, 차트·테이블 자체 컨테이너 스크롤(페이지 가로 스크롤 금지).

## 7. 레이어 / 규칙
- Controller(기간·limit 검증) → StatsService(집계 조립·빈날짜 채움) → StatsRepository(집계 쿼리).
- DTO record, 매직값 상수화(기본 30일·최대 366일·limit 기본10/최대50), Optional/빈컬렉션.
- 금액 계산은 `long` 유지(부동소수 금지), 비율만 소수.

## 8. 테스트
- 매출 계산: ACTIVE 항목만 합산(부분취소 주문에서 취소 항목 제외 확인), CONFIRMED/PARTIALLY_CANCELLED만 집계, PENDING·CANCELLED 제외.
- 취소율: 전체/전부취소/부분취소 분리 정확성, 분모 0일 때 0 반환(0으로 나누기 방지).
- 기간: from/to 경계 포함, `from > to` 400, 366일 초과 400, 미지정 시 최근 30일.
- 일별 추이: 데이터 없는 날 0으로 채움, 날짜 정렬.
- Top N: limit 기본/상한, 동일 매출 시 정렬 안정성, 취소 항목 제외.
- 권한: ADMIN 200 / SELLER 403 / USER 403 / 미인증 401.
- 성능: 집계가 단일 쿼리로 수행되는지(쿼리 수가 데이터량에 비례하지 않음).

## 9. 배포
- 스키마 변경은 인덱스 추가 시에만(Flyway 다음 버전). 실 DB 적용은 release 전 E2E로 확인.
- 판매자용 통계(V1.2-7 판매자 센터 인사이트)는 이 집계 로직을 **본인 sellerId 필터**로 재사용 예정 — Service 메서드 시그니처에 sellerId 필터 확장 여지를 남긴다.
