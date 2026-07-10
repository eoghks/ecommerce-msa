# 설계 — 상품 리뷰·별점 (V1.1-1)

## 1. 목표 / 범위
구매한 상품에 별점(1~5)과 텍스트 리뷰를 남기고, 상품 상세에 평균 별점·리뷰 목록을 노출한다.

- 포함: 리뷰 작성(구매 인증)·수정·삭제, 상품별 리뷰 목록, 상품 평균 별점/개수, 상세·목록 노출
- 제외(백로그): 리뷰 이미지 첨부, 도움돼요/신고, 리뷰 정렬 다양화, 판매자 답글

## 2. 배치 결정 — product-service
리뷰는 **상품 중심**(평균 별점은 상품 조회의 핫 리드 경로)이라 product-service가 소유. 단, "구매 여부"는 order-service가 아는 정보 → **동기 내부 호출**로 검증.

| 관심사 | 소유 |
|--------|------|
| Review 엔티티·CRUD·평균 별점 비정규화 | product-service |
| 구매 사실(주문 항목) | order-service (내부 조회 API 제공) |

## 3. 구매 인증 흐름
리뷰 작성 시 product-service → order-service 내부 호출로 "이 사용자가 이 상품을 구매(확정)했는가" 확인.

- order-service 신규 내부 엔드포인트: `GET /api/v1/orders/internal/purchased?userId=&productId=` → `{ "purchased": true|false }`
  - 판정: 해당 userId의 주문 중 productId를 포함하고 **취소되지 않은(ACTIVE) 항목**이 있는 주문이 하나라도 있으면 true. (주문 상태가 CONFIRMED 이상 = 재고 차감 완료된 실제 구매)
  - **인증**: `X-Internal-Token` 헤더 검증(기존 M-N1 패턴). order-service에 내부 토큰 검증 추가(auth-service와 동일 `${app.internal.token}` env). 외부 게이트웨이 라우팅에서 `/api/v1/orders/internal/**`는 차단(내부 전용, 게이트웨이 INTERNAL_ONLY 목록에 추가).
- product-service에 `OrderClient`(기존 `UserClient` 미러 — RestTemplate + `X-Internal-Token`) 추가.
- 호출 실패/타임아웃 시: 리뷰 작성 거부(안전한 실패). 타임아웃은 UserClient와 동일 수준(connect 2s/read 3s).

## 4. 데이터 모델 (product-service, Flyway V9)
```sql
CREATE TABLE review (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    rating      SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    content     VARCHAR(1000),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP,
    CONSTRAINT uk_review_user_product UNIQUE (user_id, product_id)
);
CREATE INDEX idx_review_product ON review (product_id, created_at DESC);

ALTER TABLE product ADD COLUMN rating_avg  NUMERIC(2,1) NOT NULL DEFAULT 0.0;
ALTER TABLE product ADD COLUMN rating_count INT         NOT NULL DEFAULT 0;
```
- **1 사용자 = 1 상품 1 리뷰**(`UNIQUE(user_id, product_id)`) — 스팸 방지, 수정으로 갱신. (주문항목 단위가 아닌 상품 단위 — MVP 단순화, 트레이드오프: 여러 번 산 사람도 1리뷰. 수용 가능.)
- rating은 DB CHECK + 애플리케이션 검증 이중.

## 5. 평균 별점 비정규화 갱신
리뷰 생성/수정/삭제 시 해당 상품의 avg/count를 **집계 재계산 UPDATE**로 갱신(같은 트랜잭션).
```sql
UPDATE product p SET
  rating_count = (SELECT count(*) FROM review r WHERE r.product_id = p.id),
  rating_avg   = COALESCE((SELECT round(avg(r.rating),1) FROM review r WHERE r.product_id = p.id), 0.0)
WHERE p.id = :productId
```
- 증분(+= )보다 재계산이 정확·단순(리뷰 쓰기는 저빈도). 동시성은 재계산 UPDATE로 최종 일관.
- **상품 상세 캐시 무효화**: 리뷰 변경 시 해당 상품 Redis 상세 캐시 삭제(기존 캐시 키 규칙 재사용) — 별점 반영 지연 방지.

## 6. API (product-service)
| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/v1/products/{productId}/reviews` | USER | 작성(구매 인증, 1인1리뷰 중복 409) |
| PUT | `/api/v1/products/{productId}/reviews/{reviewId}` | 본인 | 수정(rating/content) |
| DELETE | `/api/v1/products/{productId}/reviews/{reviewId}` | 본인 or ADMIN | 삭제 |
| GET | `/api/v1/products/{productId}/reviews` | 공개 | 목록(페이징, 최신순) |

- 게이트웨이: 작성/수정/삭제는 인증 필수 경로, 목록은 공개.
- 요청 검증: rating 1~5, content 길이(≤1000)·XSS 이스케이프, 미인증 401, 타인 리뷰 수정/삭제 403/404.
- 응답 DTO: reviewId, userId(또는 표시명 — MVP는 userId/마스킹), rating, content, createdAt.
- `ProductResponse`(상세)에 `ratingAvg`,`ratingCount` 추가. `ProductSummaryResponse`(목록)에도 추가(정렬 확장 대비).

## 7. 프론트
- 상품 상세: 평균 별점(★ x.x, n개) 표시 + **리뷰 탭**(목록 페이징). 구매자면 작성 폼(별점 선택 + 내용), 이미 작성했으면 수정/삭제.
- 내 주문 화면: 구매 확정 항목에 **"리뷰 쓰기"** 버튼 → 해당 상품 리뷰 작성.
- 비구매/비로그인은 작성 불가(안내). 실패 피드백(구매 인증 실패 400/409) 노출.

## 8. 레이어 / 규칙
- Controller(검증)→ReviewService(구매 인증 호출·비정규화 갱신·캐시 무효화)→ReviewRepository.
- DTO record, Optional/빈컬렉션, 매직값 상수화(최대 길이/별점 범위). JPA 파라미터 바인딩(${} 금지).
- 구매 인증 실패/중복은 도메인/서비스 분기 또는 명확한 예외+핸들러 매핑.

## 9. 테스트
- 구매자 작성 성공·비구매자 403/400·중복 작성 409·rating 범위 밖 400·본인 수정/삭제·타인 수정삭제 차단·목록 페이징·평균/개수 갱신(생성/수정/삭제 반영)·캐시 무효화·내부호출 실패 시 거부.

## 10. 배포
- Flyway `V9`(product). order-service 내부 엔드포인트 + 내부토큰 검증 추가 → 배포 체크리스트의 INTERNAL_TOKEN 동기화 대상에 order-service 포함(문서 갱신).
- 실 DB 마이그레이션·구매 인증 크로스서비스 흐름은 release 전 E2E로 검증.
