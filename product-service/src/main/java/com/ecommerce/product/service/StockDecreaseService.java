package com.ecommerce.product.service;

import com.ecommerce.product.event.OrderCreatedPayload;
import com.ecommerce.product.event.StockEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 재고 차감 서비스 — Redis 분산 락 + 멱등성 체크 오케스트레이션.
 *
 * Redis Lock 전략:
 *   - 락 키: "stock:lock:order:{orderId}" — 동일 주문 이벤트 중복 처리 방지
 *   - 멱등성 키: "stock:processed:{orderId}" — 차감 성공 확정 후 설정 (H-02 수정)
 *
 * C-02 수정: doDecreaseStock() 을 StockDecreaseTransactionService(별도 Bean)로 분리
 *   → Spring AOP 프록시를 통해 호출되어 @Transactional 정상 적용
 *
 * H-02 수정: 멱등성 키를 재고 차감 성공 후에 설정
 *   → JVM 크래시 등 중간 실패 시 키 잔류로 인한 영구 skip 방지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockDecreaseService {

    private static final String LOCK_KEY_PREFIX      = "stock:lock:order:";
    private static final String PROCESSED_KEY_PREFIX = "stock:processed:";
    private static final long   LOCK_WAIT_SECONDS    = 3L;
    private static final long   LOCK_LEASE_SECONDS   = 10L;
    private static final Duration PROCESSED_TTL      = Duration.ofDays(1);

    private final RedisTemplate<String, String>       redisTemplate;
    private final RedissonClient                      redissonClient;
    private final StockEventPublisher                 stockEventPublisher;
    private final StockDecreaseTransactionService     stockDecreaseTransactionService;

    /**
     * Redis 분산 락 → 재고 차감 → 멱등성 키 설정 → 결과 이벤트 발행.
     */
    public void decreaseStock(OrderCreatedPayload payload) {
        Long   orderId      = payload.orderId();
        String lockKey      = LOCK_KEY_PREFIX + orderId;
        String processedKey = PROCESSED_KEY_PREFIX + orderId;

        // 이미 처리된 주문이면 skip (락 없이 빠른 체크)
        if (Boolean.TRUE.equals(redisTemplate.hasKey(processedKey))) {
            log.info("이미 처리된 주문 이벤트 — skip. orderId={}", orderId);
            return;
        }

        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (!lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
                log.warn("재고 락 획득 실패 — 재시도 대기. orderId={}", orderId);
                throw new IllegalStateException("재고 락 획득 실패. orderId=" + orderId);
            }

            // 락 내부에서 이중 체크 — 경쟁 조건 방지
            if (Boolean.TRUE.equals(redisTemplate.hasKey(processedKey))) {
                log.info("이미 처리된 주문 이벤트 (락 내부 체크) — skip. orderId={}", orderId);
                return;
            }

            // 재고 차감 (별도 Bean 호출 → @Transactional 정상 적용)
            stockDecreaseTransactionService.decreaseStock(payload);

            // 차감 성공 후 멱등성 키 설정 (H-02: 성공 확정 후 설정)
            redisTemplate.opsForValue().set(processedKey, "1", PROCESSED_TTL);

        } catch (IllegalStateException ex) {
            // 재고 부족 or 락 실패 — 보상 이벤트 발행
            stockEventPublisher.publishStockDecreaseFailed(orderId, ex.getMessage());
            return;

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            stockEventPublisher.publishStockDecreaseFailed(orderId, "락 대기 중 인터럽트 발생");
            return;

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        stockEventPublisher.publishStockDecreased(orderId);
    }
}
