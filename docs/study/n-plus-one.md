# N+1 문제 & QueryDSL 선택

> 면접 대비 정리 문서

---

## N+1 문제란

목록 조회(1번) 후 연관 엔티티에 접근할 때 N번 추가 쿼리가 발생하는 문제.

```java
List<Product> products = productRepository.findAll(); // 1번

products.forEach(p -> p.getCategory().getName());
// 상품마다 category 조회 → N번
// 상품 100개면 총 101번 쿼리
```

```sql
SELECT * FROM product;                 -- 1번
SELECT * FROM category WHERE id = 1;  -- 상품1
SELECT * FROM category WHERE id = 2;  -- 상품2
...                                    -- 상품 100개면 101번
```

---

## 발생 원인

`FetchType.LAZY` — 연관 엔티티를 실제 접근 시점에 조회하기 때문.

Product를 조회해도 category는 프록시 객체로만 존재하다가 `getCategory().getName()` 호출 시 그때 DB 쿼리가 나감.

---

## 해결 방법

### 1. fetch join (JPQL)
```java
SELECT p FROM Product p JOIN FETCH p.category
```
Product + Category를 한 번에 조회. 쿼리 1번.

### 2. @EntityGraph
```java
@EntityGraph(attributePaths = "category")
List<Product> findAll();
```
fetch join과 동일한 동작. 단순 메서드에 어노테이션으로 표현.

### 3. default_batch_fetch_size
```yaml
spring.jpa.properties.hibernate.default_batch_fetch_size: 100
```
```sql
SELECT * FROM category WHERE id IN (1, 2, 3, ... 100)  -- 1번으로 해결
```
IN 절로 한꺼번에 조회.

---

## 언제 뭘 쓰냐

| 상황 | 선택 |
|------|------|
| 단순 조회, 항상 같이 필요 | fetch join 또는 @EntityGraph |
| 동적 조건 (nullable 파라미터) | QueryDSL + fetch join |
| 컬렉션(@OneToMany) + 페이징 | batch_fetch_size |

> 컬렉션에 fetch join + 페이징 동시 사용 시 `HHH90003004` 경고 발생.
> Hibernate가 전체 데이터를 메모리에 올려서 페이징 → 이때는 batch_fetch_size 사용.

---

## 이 프로젝트에서 QueryDSL을 선택한 이유

상품 목록 조회 조건이 동적입니다.

```
GET /api/v1/products                             → 전체 조회
GET /api/v1/products?categoryId=1                → 카테고리만
GET /api/v1/products?keyword=노트북               → 키워드만
GET /api/v1/products?categoryId=1&keyword=노트북  → 둘 다
```

어떤 조건이 올지 런타임에 결정되기 때문에 Spring Data JPA 메서드 네이밍(컴파일 타임 고정)으로는 처리 불가.

```java
// JPQL — null 조건을 쿼리 안에서 처리, 지저분
WHERE (:categoryId IS NULL OR c.id = :categoryId)
  AND (:keyword IS NULL OR p.name LIKE %:keyword%)

// QueryDSL — null이면 조건 자체를 제외, 깔끔
if (categoryId != null) builder.and(category.id.eq(categoryId));
if (keyword != null)    builder.and(product.name.contains(keyword));
```

추가로 카운트 쿼리를 분리해서 불필요한 fetch join 제거.

```java
// 목록 쿼리 — fetch join 포함 (N+1 방지)
// 카운트 쿼리 — fetch join 제외 (COUNT에 JOIN 불필요)
```

---

## 면접 예상 질문

**Q. N+1 문제가 뭔가요?**
> LAZY 로딩 상태에서 목록 조회 후 연관 엔티티에 접근할 때 N번 추가 쿼리가 발생하는 문제입니다. 상품 100개를 조회하면 카테고리 조회 쿼리가 100번 추가로 나가서 총 101번 쿼리가 실행됩니다.

**Q. 어떻게 해결했나요?**
> fetch join으로 해결했습니다. 상품 목록 조회 시 카테고리를 한 번에 같이 가져오도록 QueryDSL로 구현했습니다. JPQL 대신 QueryDSL을 선택한 이유는 카테고리 필터, 키워드 검색 등 동적 조건이 있어서 컴파일 타임에 쿼리를 고정할 수 없었기 때문입니다.

**Q. @EntityGraph랑 fetch join 차이가 뭔가요?**
> 동작은 동일합니다. @EntityGraph는 단순한 메서드에 어노테이션으로 표현할 때 편리하고, fetch join은 동적 조건이 있는 복잡한 쿼리에 적합합니다. 동적 조건이 있어서 fetch join을 선택했습니다.
