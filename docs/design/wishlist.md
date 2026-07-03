# 설계 — 위시리스트(찜) (V1.1-2)

## 1. 목표 / 범위
로그인 사용자가 상품을 찜 목록에 추가/삭제하고 마이페이지에서 조회한다.

- 포함: 찜 추가, 찜 해제, 내 찜 목록(상품 요약 + 페이징), 찜 여부 조회(상세/카드 하트 표시)
- 제외: 찜 알림, 찜 공유, 재입고 알림(→ v1.1 알림 기능과 추후 연계)

## 2. 배치 결정 — product-service
| 후보 | 장점 | 단점 |
|------|------|------|
| **product-service (채택)** | 상품 정보 직접 조인(크로스서비스 호출 없음), 판매금지/삭제 상품 필터 용이 | 사용자 컨텍스트는 X-User-Id 헤더 의존(기존 패턴과 동일) |
| order-service (cart 옆) | 장바구니와 사용자 쇼핑 컨텍스트 응집 | 상품 요약 조회 시 product 호출 필요(N+1/지연) |

→ 위시리스트는 **상품 북마크**이고 목록 표시에 상품 정보가 필수라, product-service에 두어 조인으로 해결. 사용자 식별은 게이트웨이가 주입하는 `X-User-Id`(기존 createProduct와 동일 패턴).

## 3. 저장 방식 — DB(JPA) 테이블
장바구니가 이미 DB 기반이라 일관성 위해 DB 채택. 조회·페이징·상품 조인이 쉽다.

```sql
-- Flyway V7 (product-service)
CREATE TABLE wishlist (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    product_id  BIGINT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_wishlist_user_product UNIQUE (user_id, product_id)
);
CREATE INDEX idx_wishlist_user ON wishlist (user_id, created_at DESC);
```
- `UNIQUE(user_id, product_id)` — 중복 찜 방지(멱등).
- product FK는 물리 제약 대신 애플리케이션 정합으로 관리(상품 삭제 시 정리는 4.5 참고).

## 4. API 설계 (`/api/v1/wishlist`, 인증 필요)
게이트웨이에서 `/api/v1/wishlist/**`는 인증 필수 라우팅. 컨트롤러는 `@RequestHeader("X-User-Id") Long userId` 수신, null이면 401.

| 메서드 | 경로 | 설명 | 응답 |
|--------|------|------|------|
| POST | `/api/v1/wishlist/{productId}` | 찜 추가(멱등 — 이미 있으면 그대로 200/204) | 204 |
| DELETE | `/api/v1/wishlist/{productId}` | 찜 해제(없어도 204, 멱등) | 204 |
| GET | `/api/v1/wishlist/me` | 내 찜 목록(페이징, 상품 요약) | 200 Page\<WishlistItemResponse> |
| GET | `/api/v1/wishlist/me/ids` | 내 찜 상품 ID 집합(하트 표시용, 경량) | 200 Set\<Long> |

### 4.1 추가
- 상품 존재 확인(없으면 404). 판매금지/삭제 상품 찜은 거부(400) 또는 허용? → **추가 시점엔 판매중 상품만 허용**(404/400), 이후 상품이 금지돼도 목록에선 표시하되 "판매중지" 뱃지(4.4).
- `INSERT ... ON CONFLICT DO NOTHING` 대신 JPA: existsBy로 확인 후 없으면 저장(유니크 제약이 최종 방어).

### 4.2 해제
- 본인 소유 찜만 삭제(user_id 조건 포함). 없으면 조용히 204(멱등).

### 4.3 목록
- `WishlistItemResponse`: productId, name, price, imageUrl, status(판매중/판매중지), createdAt(찜한 시각).
- 페이징(기본 size 20, createdAt DESC). 상품 조인으로 한 방 조회(N+1 금지).

### 4.4 판매금지/삭제 상품 처리
- 목록 조회 시 상품이 BANNED면 status="판매중지"로 표시(제거하지 않음 — 사용자 찜 이력 보존).
- 상품이 물리 삭제된 경우: 조인 결과 없음 → 목록에서 제외(또는 표시 후 정리). MVP는 **제외**.

### 4.5 상품 삭제 시 정리(선택)
- product 삭제 시 wishlist 잔존 로우는 조회에서 조인 실패로 자연 배제. 별도 배치 정리는 백로그로 남김(MVP 제외).

## 5. 프론트
- 상품 카드/상세에 하트 토글: 진입 시 `/wishlist/me/ids`로 찜 여부 일괄 판단, 토글 시 POST/DELETE 낙관적 업데이트.
- 마이페이지 "찜 목록" 탭: `/wishlist/me` 페이징 목록, 항목 클릭 시 상세 이동, 하트 해제 시 목록에서 제거.
- 비로그인 시 하트 클릭 → 로그인 유도.

## 6. 레이어 / 규칙
- Controller(입력·헤더 검증) → WishlistService(비즈니스) → WishlistRepository(JPA).
- DTO는 record. Optional/빈 컬렉션 반환. 매직값 상수화.
- 목록/ID 조회 `@Transactional(readOnly=true)`, 추가/삭제 쓰기 트랜잭션.

## 7. 테스트
- 추가(신규/중복 멱등)·해제(존재/미존재 멱등)·타인 찜 삭제 불가·목록 페이징·ID 집합·판매금지 상품 status 표기·없는 상품 추가 404·userId null 401.

## 8. 마이그레이션 / 배포
- Flyway `V7__wishlist.sql`. 실 DB 적용은 기동 시 검증(V6 사례처럼 release 전 E2E로 확인).
