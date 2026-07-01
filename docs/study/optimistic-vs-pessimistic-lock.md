# 낙관적 락 vs 비관적 락

> 면접 대비 정리 문서

---

## 낙관적 락 (Optimistic Lock)

`@Version` 필드로 충돌을 감지합니다.

```java
@Entity
public class Product {
    @Version
    private Long version;  // Hibernate가 자동 관리
}
```

```
트랜잭션A: SELECT → stock=10, version=1
트랜잭션B: SELECT → stock=10, version=1

트랜잭션A: UPDATE SET stock=9, version=2 WHERE id=1 AND version=1 → 성공
트랜잭션B: UPDATE SET stock=9, version=2 WHERE id=1 AND version=1 → 실패 (version이 이미 2)
           → OptimisticLockException 발생 → 재시도
```

DB 락 없이 version 비교로 충돌 감지. 충돌 시 재시도 로직 필요.

---

## 비관적 락 (Pessimistic Lock)

SELECT FOR UPDATE로 행을 선점합니다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Optional<Product> findByIdWithLock(@Param("id") Long id);
```

```
트랜잭션A: SELECT FOR UPDATE → 행 잠금
트랜잭션B: SELECT FOR UPDATE → 대기 (블로킹)

트랜잭션A: 처리 완료 → COMMIT → 락 해제
트랜잭션B: 락 획득 → 처리
```

---

## 비교

| | 낙관적 락 | 비관적 락 |
|---|---|---|
| 방식 | 충돌 감지 후 재시도 | 선점 후 대기 |
| DB 락 | 없음 | SELECT FOR UPDATE |
| 충돌 빈도 낮을 때 | 빠름 | 불필요한 대기 발생 |
| 충돌 빈도 높을 때 | 재시도 폭탄 | 순차 처리로 안정적 |
| DB 커넥션 점유 | 짧음 | 락 해제까지 점유 |

---

## 언제 뭘 쓰냐

| 상황 | 선택 |
|------|------|
| 재고 차감 — 동시 충돌 잦음 | 비관적 락 |
| 회원 정보 수정 — 충돌 드묾 | 낙관적 락 |
| MSA 멀티 인스턴스 | Redisson 분산락 |

---

## 이 프로젝트 전략

- Week 3: 비관적 락 — 단순하고 안전
- Week 4: Redisson 분산락으로 전환 — MSA 멀티 인스턴스 대응

---

## 면접 예상 질문

**Q. 낙관적 락 언제 쓰나요?**
> 충돌 빈도가 낮을 때 적합합니다. 비관적 락은 충돌 여부와 관계없이 항상 DB 락을 걸어서 대기가 생기는데, 낙관적 락은 실제 충돌 시에만 재시도하므로 충돌이 드문 상황에서 성능이 좋습니다.

**Q. 재고 차감에 낙관적 락을 안 쓴 이유는요?**
> 재고 차감은 동시 요청이 집중되는 케이스라 충돌 빈도가 높습니다. 낙관적 락은 충돌 시 재시도가 반복되면 재시도 폭탄이 발생할 수 있어서 비관적 락을 선택했습니다.

**Q. 비관적 락 단점은요?**
> DB 커넥션을 락 해제까지 점유해서 트래픽이 많으면 커넥션 풀 고갈 위험이 있습니다. 그래서 고트래픽 환경에서는 Redisson 분산락으로 전환했습니다.
