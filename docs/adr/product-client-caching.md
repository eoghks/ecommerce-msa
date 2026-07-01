# ADR: ProductClient 캐싱 전략

## 결정

order-service에서 ProductClient 호출 시 별도 캐시 추가 없이 현행 구조 유지.

## 배경

장바구니에 상품을 담을 때 order-service의 CartService가 ProductClient를 통해
product-service HTTP 호출로 가격·상품명·imageUrl을 조회한다.

## 분석

| 레이어 | 내용 |
|---|---|
| order-service | `ProductClient.getProduct()` → HTTP 호출 |
| product-service | 요청 수신 후 `redis-product(6380)` 캐시 조회 |
| redis-product | 캐시 HIT → DB 미조회, 즉시 반환 |

product-service가 이미 `redis-product` 인스턴스에 상품 상세 캐시를 운영 중이므로
order-service에서 HTTP 요청을 보내더라도 product-service 레벨에서 캐시 HIT으로 응답한다.
추가 HTTP 오버헤드는 로컬 네트워크(MSA 내부 통신) 수준이라 무시 가능하다.

## 결론

order-service 측에 product 정보 캐시를 별도로 두는 것은 현재 규모에서 오버엔지니어링.
현행 구조 유지.

## 향후 확장 조건

트래픽이 증가하여 HTTP 호출 자체가 병목이 될 경우 아래 패턴으로 전환:

1. order-service `redis-order`에 `product:{id}` 키로 캐싱 (TTL 5분)
2. product-service에서 상품 수정 시 `product.updated` Kafka 이벤트 발행
3. order-service 컨슈머가 이벤트 수신 후 해당 캐시 키 evict

## Redis 인스턴스 구조 (참고)

| 인스턴스 | 포트 | 담당 서비스 | 용도 |
|---|---|---|---|
| redis-auth | 6379 | auth-service | Refresh Token 세션 |
| redis-product | 6380 | product-service | 상품 상세 캐시 |
| redis-order | 6381 | order-service | 게스트 장바구니 |
