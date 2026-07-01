# Product Service — 테스트 전략 및 결과

---

## 1. 테스트 구성

| 레이어 | 클래스 | 방식 | 건수 |
|--------|--------|------|------|
| 도메인 단위 | `ProductDomainTest` | 순수 JUnit5 (Spring 없음) | 5건 |
| 서비스 단위 | `ProductServiceTest` | `@ExtendWith(MockitoExtension)` | 12건 |
| 레포지토리 통합 | `ProductRepositoryTest` | `@DataJpaTest` + H2 인메모리 | 6건 |
| API E2E | `ProductControllerE2ETest` | `@SpringBootTest(RANDOM_PORT)` + 실 서비스 연결 | 10건 |
| **합계** | | | **33건 / 실패 0건** |

---

## 2. 테스트 레이어별 상세

### 2-1. 도메인 단위 테스트 (`ProductDomainTest`)

Spring Context 없이 순수 Java로 도메인 로직만 검증.

| 테스트 | 검증 내용 |
|--------|----------|
| 재고 차감 — 정상 | `decreaseStock(3)` 후 stock = 7 |
| 재고 차감 — 전량 | `decreaseStock(5)` 후 stock = 0 |
| 재고 차감 — 부족 | `decreaseStock(5)` stock=2 → `IllegalStateException` |
| 재고 복구 | `increaseStock(3)` 후 stock = 8 |
| 상품 정보 수정 | `update()` 후 전체 필드 반영 확인 |

### 2-2. 서비스 단위 테스트 (`ProductServiceTest`)

Mockito로 Repository, RedisTemplate을 Mock 처리. 비즈니스 로직과 캐시 동작만 검증.

| 테스트 | 검증 내용 |
|--------|----------|
| 상품 등록 정상 | `productRepository.save()` 호출 확인 |
| 상품 등록 — 카테고리 없음 | `CategoryNotFoundException` 발생 |
| 상품 수정 정상 | 캐시 무효화(`redis.delete`) 확인 |
| 상품 수정 — 상품 없음 | `ProductNotFoundException` 발생 |
| 상품 삭제 정상 | `delete()` + 캐시 무효화 확인 |
| 상품 삭제 — 상품 없음 | `ProductNotFoundException` 발생, `delete()` 미호출 |
| 상세 조회 — 캐시 히트 | DB 조회 없음, TTL 리셋(`expire`) 확인 |
| 상세 조회 — 캐시 미스 | DB 조회 후 `valueOperations.set()` 확인 |
| 상세 조회 — 상품 없음 | `ProductNotFoundException` 발생 |
| 목록 조회 — 필터 없음 | `findAllWithFilter(null, null, pageable)` 결과 확인 |
| 목록 조회 — 복합 필터 | `categoryId + keyword` 필터 결과 확인 |

### 2-3. 레포지토리 통합 테스트 (`ProductRepositoryTest`)

`@DataJpaTest` + H2 인메모리 DB. QueryDSL 동적 쿼리 동작 검증.

> Docker 없이 실행 가능. Flyway 비활성화, `ddl-auto=create-drop` 적용.

| 테스트 | 검증 내용 |
|--------|----------|
| 필터 없음 | 전체 3건 조회 |
| 카테고리 필터 | 전자기기 2건만 조회 |
| 키워드 필터 | '갤럭시' 포함 1건 조회 |
| 복합 필터 | categoryId + keyword 교집합 1건 |
| 빈 결과 | 존재하지 않는 키워드 → 0건 빈 페이지 |
| 페이징 | size=2 → content 2건, totalElements 3건, totalPages 2 |

### 2-4. API E2E 테스트 (`ProductControllerE2ETest`)

`@SpringBootTest(RANDOM_PORT)` + 실행 중인 docker-compose 서비스 연결 (PostgreSQL:5433, Redis:6379).

> **사전 조건**: `docker-compose up` 상태 필요.  
> 테스트 데이터는 `E2E_` prefix로 격리. `@BeforeAll`/`@AfterAll`에서 Redis Flush + DB 정리.

| 순서 | 테스트 | 검증 내용 |
|------|--------|----------|
| 1 | 상품 등록 — ADMIN | HTTP 201, 응답 필드 일치 |
| 2 | 상품 등록 — 권한 없음 | HTTP 403 |
| 3 | 상세 조회 — 캐시 미스 | HTTP 200, Redis에 캐시 저장 확인 |
| 4 | 상세 조회 — 캐시 히트 | HTTP 200, Redis 캐시 유지 확인 |
| 5 | 상세 조회 — 없는 상품 | HTTP 404 |
| 6 | 목록 조회 — 필터 없음 | HTTP 200, 상품명 포함 확인 |
| 7 | 목록 조회 — 키워드 | HTTP 200, 필터 결과 확인 |
| 8 | 목록 조회 — 카테고리 | HTTP 200, 카테고리 필터 결과 확인 |
| 9 | 상품 수정 — ADMIN | HTTP 200, 캐시 무효화 확인 |
| 10 | 상품 삭제 — ADMIN | HTTP 204, 이후 조회 시 404 |

---

## 3. 수정 사항 (테스트 중 발견된 버그)

| 파일 | 수정 내용 |
|------|----------|
| `ProductExceptionHandler` | `AuthorizationDeniedException` 핸들러 추가 → 403 반환 (기존 500 → 403) |
| `RedisConfig` | `JavaTimeModule` 추가 → `LocalDateTime` 직렬화 지원 |

### `AuthorizationDeniedException` 500 → 403

`@PreAuthorize` 인가 실패 시 Spring Security 6.x가 `AuthorizationDeniedException`을 던지는데, `@RestControllerAdvice`가 먼저 잡아 500을 반환하는 문제.  
`ProductExceptionHandler`에 `@ExceptionHandler(AuthorizationDeniedException.class)` 추가로 해결.

### Redis `LocalDateTime` 직렬화 오류

`GenericJackson2JsonRedisSerializer`의 기본 Jackson ObjectMapper에 `JavaTimeModule`이 없어 `LocalDateTime` 필드를 직렬화하지 못하는 문제.  
`JavaTimeModule` 등록 + `WRITE_DATES_AS_TIMESTAMPS=false` 설정으로 ISO-8601 문자열 포맷으로 저장.

---

## 4. 실행 방법

```bash
# 단위 + 레포지토리 테스트 (Docker 불필요)
./gradlew :product-service:test \
  --tests "com.ecommerce.product.domain.*" \
  --tests "com.ecommerce.product.service.*" \
  --tests "com.ecommerce.product.repository.*"

# E2E 테스트 (docker-compose up 필요)
./gradlew :product-service:test \
  --tests "com.ecommerce.product.e2e.*"

# 전체 테스트
./gradlew :product-service:test
```
