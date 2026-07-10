# 설계 — 상품 검색 고도화 (V1.1-8)

## 1. 목표 / 범위
현재 검색은 `categoryId + keyword + Pageable`만 지원(정렬은 Pageable sort 원시 노출). 탐색성을 높이기 위해 **정렬 옵션·가격대 필터·자동완성**을 추가한다.

- 포함: 정렬(최신/가격↑/가격↓/이름), 가격대 필터(min/max), 키워드 자동완성
- 제외(백로그): 인기순(주문수 집계 필요 → 통계/주문 연동), 별점순(리뷰 기능 V1.1-1 선행 필요), Elasticsearch 도입

## 2. 현황
- `GET /api/v1/products?categoryId&keyword&includeBanned&page&size&sort`
- 내부: `productRepository.findAllWithFilter(categoryId, keyword, enrichSeller, pageable)` (QueryDSL 추정), keyword는 name/description LIKE.
- 정렬은 스프링 Pageable `sort` 파라미터를 그대로 노출 → 클라이언트가 엔티티 필드명을 알아야 하고, 허용 필드 화이트리스트가 없음(임의 필드 정렬 시도 가능).

## 3. 정렬 옵션 (화이트리스트)
클라이언트가 엔티티 필드명을 직접 쓰지 않도록 **의미 기반 정렬 키**를 받는다.

| sort 값 | 정렬 기준 | 방향 |
|---------|-----------|------|
| `latest`(기본) | createdAt | DESC |
| `price_asc` | price | ASC |
| `price_desc` | price | DESC |
| `name` | name | ASC |

- 요청: `GET /api/v1/products?...&sort=price_asc`
- 서버가 sort 값 → 실제 정렬 컬럼 매핑(화이트리스트). 미허용/미지정 시 `latest`.
- 기존 Spring `Pageable`의 sort 원시 노출은 제거하고 `sort` 문자열 파라미터로 대체(하위호환: 기존 프론트는 sort 미전달 → latest로 동작).
- **정렬 키는 enum 또는 상수 맵으로** — 매직 문자열 방지, 허용 외 값 차단.

## 4. 가격대 필터
| 파라미터 | 설명 |
|----------|------|
| `minPrice` | 최소가(포함), null 허용 |
| `maxPrice` | 최대가(포함), null 허용 |

- 검증: min/max ≥ 0, min ≤ max(위반 시 400). Controller 진입 검증.
- QueryDSL where에 `price >= min`, `price <= max` 동적 조건 추가.
- `ProductSearchRequest`에 `minPrice, maxPrice, sort` 필드 추가.

## 5. 자동완성
| 항목 | 설계 |
|------|------|
| API | `GET /api/v1/products/suggestions?keyword=...&limit=10` |
| 반환 | 상품명 후보 목록(최대 limit, 기본 10) |
| 소스 | 상품명 prefix/부분일치 — MVP는 DB LIKE `keyword%`(distinct name, 판매중 한정, limit) |
| 성능 | 초기엔 DB 인덱스(name)로 충분. 트래픽 증가 시 Redis Sorted Set(인기검색어) 또는 Elasticsearch로 확장 — **트레이드오프 문서화 후** 별도 진행 |

- 입력 검증: keyword 공백/최소 1자, limit 상한(예 20). 빈 keyword → 빈 목록.
- 판매금지/삭제 상품 제외.

## 6. API 요약(확장 후)
```
GET /api/v1/products
    ?categoryId=&keyword=
    &minPrice=&maxPrice=
    &sort=latest|price_asc|price_desc|name
    &page=&size=
GET /api/v1/products/suggestions?keyword=&limit=
```
- 하위호환: 신규 파라미터 전부 optional. 미전달 시 기존 동작.

## 7. 레이어 / 규칙
- Controller: 신규 파라미터 바인딩·검증(min≤max, limit 상한) → ProductSearchRequest 조립.
- Service/Repository: QueryDSL 동적 where(가격) + 정렬 화이트리스트 매핑. `${}` 문자열 결합 금지(파라미터 바인딩).
- 정렬 키 매핑은 enum(SortOption)으로. 자동완성은 별도 조회 메서드.

## 8. 프론트
- 목록 화면: 정렬 드롭다운(최신/가격↑/가격↓/이름), 가격대 min/max 입력.
- 검색바: 입력 시 debounce(예 250ms)로 `/suggestions` 호출, 후보 드롭다운, 선택 시 검색 실행.

## 9. 테스트
- 정렬 각 옵션 결과 순서·미허용 sort 값 → latest 폴백.
- 가격 필터(min만/ max만/둘다/역전 400).
- 자동완성(prefix 매칭·limit 상한·빈 keyword 빈 결과·판매금지 제외).
- 하위호환(파라미터 미전달 시 기존과 동일).

## 10. 배포
- DB 스키마 변경 없음(name 인덱스만 필요 시 Flyway로 추가). 인덱스 추가 시 실 DB 적용 검증.
