# 코딩 규칙

## 공통
- Java 21 사용
- `jakarta.*` 사용 (`javax.*` 금지)
- Lombok 적극 사용: `@Getter` `@Setter` `@Builder` `@Slf4j` `@RequiredArgsConstructor`
- 한국어 주석 허용
- 메서드는 30줄 이내 유지 (초과 시 분리)
- null 반환 대신 `Optional` 또는 빈 컬렉션 반환
- 매직 넘버/문자열은 상수로 선언
- 함수 인자로 `Map` 대신 명시적 DTO/파라미터 사용

## RESTful API 설계 원칙

### HTTP Method 의미대로 사용
| Method | 용도 | 멱등성 |
|--------|------|--------|
| `GET` | 조회 | ✅ |
| `POST` | 생성 | ❌ |
| `PUT` | 전체 수정 | ✅ |
| `PATCH` | 부분 수정 | ❌ |
| `DELETE` | 삭제 | ✅ |

### URI는 리소스 중심 (동사 금지)
```
❌ POST /api/createProduct
❌ GET  /api/getProduct/1
✅ POST /api/products
✅ GET  /api/products/1
✅ GET  /api/products/1/reviews    # 하위 리소스
```

### HTTP Status Code 정확히 사용
| Code | 상황 |
|------|------|
| `200 OK` | 조회/수정 성공 |
| `201 Created` | 생성 성공 (+ `Location` 헤더 필수) |
| `204 No Content` | 삭제 성공, 응답 바디 없음 |
| `400 Bad Request` | 입력값 검증 실패 |
| `401 Unauthorized` | 인증 실패 (토큰 없음/만료) |
| `403 Forbidden` | 권한 없음 |
| `404 Not Found` | 리소스 없음 |
| `409 Conflict` | 중복(이메일 등), 재고 부족 |
| `422 Unprocessable Entity` | 비즈니스 규칙 위반 |
| `500 Internal Server Error` | 서버 오류 |

## 레이어 책임

### Controller
- 요청/응답 처리, 입력값 바인딩만
- 비즈니스 로직 금지
- 응답은 `ResponseEntity<도메인Response>` 직접 반환 (래퍼 사용 금지)
- 성공 응답은 적절한 HTTP Status Code 반환 (200/201/204)
- 실패는 예외 throw — `@RestControllerAdvice` 가 처리
- 입력값 검증: `@Valid` + DTO에 Bean Validation 적용

```java
@PostMapping
public ResponseEntity<ProductResponse> create(@RequestBody @Valid CreateProductRequest req) {
    ProductResponse created = productService.create(req);
    return ResponseEntity
            .created(URI.create("/api/products/" + created.id()))
            .body(created);
}

@DeleteMapping("/{id}")
public ResponseEntity<Void> delete(@PathVariable Long id) {
    productService.delete(id);
    return ResponseEntity.noContent().build();
}
```

### Service
- 비즈니스 로직 전담
- 트랜잭션 경계 관리 (`@Transactional`)
- 조회 메서드는 `@Transactional(readOnly = true)` 적용

### Repository
- DB 접근만 — Spring Data JPA Repository 호출만 허용
- 단순 쿼리는 Spring Data JPA 메서드 네이밍 사용
- 복잡한 쿼리는 QueryDSL 사용 (JPQL 문자열 사용 지양)
- 네이티브 쿼리 최소화

## 예외 처리

### 원칙
- 비즈니스 실패는 커스텀 예외 throw (Controller에서 try/catch 금지)
- 전역 예외는 `@RestControllerAdvice` 에서 처리
- 에러 응답은 RFC 7807 `ProblemDetail` 표준 사용 (Spring 6 내장)
- 예외 메시지는 사용자에게 노출되지 않도록 추상화

### ProblemDetail 응답 예시
```json
{
  "type": "https://api.eoghks.com/errors/stock-insufficient",
  "title": "재고 부족",
  "status": 409,
  "detail": "상품 ID 5의 재고가 부족합니다",
  "instance": "/api/orders"
}
```

### 전역 핸들러 예시
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(EntityNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(NOT_FOUND, e.getMessage());
        problem.setTitle("리소스를 찾을 수 없음");
        return ResponseEntity.status(NOT_FOUND).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(BAD_REQUEST, "입력값 검증 실패");
        problem.setProperty("errors", e.getBindingResult().getFieldErrors().stream()
                .map(err -> Map.of("field", err.getField(), "message", err.getDefaultMessage()))
                .toList());
        return ResponseEntity.badRequest().body(problem);
    }
}
```

## 파일 생성 규칙
- `.java` `.jsx` `.js` `.xml` 파일 생성 후 즉시 `git add <파일경로>` 실행
