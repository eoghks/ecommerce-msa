# OSIV (Open Session In View)

> 면접 대비 정리 문서

---

## OSIV란

HTTP 요청이 들어올 때부터 응답이 나갈 때까지 EntityManager(DB 커넥션)를 열어두는 옵션.

Spring Boot 기본값: `true`

---

## OSIV ON 동작

```
요청 시작 → EntityManager 오픈 → DB 커넥션 획득
  └→ Controller
  └→ Service (@Transactional)
  └→ Repository
  └→ Controller (트랜잭션 밖에서도 LAZY 로딩 가능)
  └→ JSON 직렬화
응답 완료 → EntityManager 종료 → DB 커넥션 반납
```

트랜잭션 밖(Controller)에서도 LAZY 로딩이 가능한 이유가 이것.

---

## 왜 끄는 게 권장되냐

DB 커넥션을 요청 전체 동안 점유.

```
OSIV ON
요청 → 커넥션 획득
  Service 로직 (1초)
  Controller JSON 직렬화 (0.1초)  ← 여기서도 커넥션 점유
응답 → 커넥션 반납  (총 1.1초 점유)

OSIV OFF
요청
  Service 로직 (1초) → 커넥션 반납  (1초만 점유)
  Controller JSON 직렬화 (0.1초)  ← 커넥션 없음
응답
```

트래픽 많으면 커넥션 풀 고갈 위험.

---

## OSIV OFF 시 패턴

Service에서 트랜잭션 안에 DTO 변환까지 완료 후 반환.

```java
@Transactional(readOnly = true)
public ProductResponse getProduct(Long id) {
    Product product = productRepository.findById(id)
        .orElseThrow(() -> new ProductNotFoundException(id));

    // 트랜잭션 안에서 LAZY 로딩 + DTO 변환 완료
    return new ProductResponse(
        product.getId(),
        product.getName(),
        product.getCategory().getName()
    );
}
// Controller에는 DTO만 전달 → 커넥션 이미 반납
```

---

## 설정

```yaml
# 전 서비스 공통 적용
spring:
  jpa:
    open-in-view: false
```

---

## 면접 예상 질문

**Q. OSIV가 뭔가요?**
> HTTP 요청 동안 EntityManager를 열어두는 옵션입니다. Spring Boot 기본값이 true인데 DB 커넥션을 요청 전체 동안 점유해서 트래픽이 많으면 커넥션 풀 고갈 위험이 있습니다.

**Q. OSIV 끄면 LazyInitializationException 어떻게 해결하나요?**
> Service 레이어에서 트랜잭션 안에 필요한 연관 데이터를 미리 로딩하고 DTO로 변환 후 반환합니다. Controller에는 DTO만 전달해서 커넥션을 일찍 반납합니다.
