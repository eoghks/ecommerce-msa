package com.ecommerce.product.service;

import com.ecommerce.product.event.OrderItemCancelledPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 재고 복구 서비스 — 항목 취소 이벤트 수신 시 재고를 되돌린다.
 *
 * 멱등성이 핵심: Kafka at-least-once 재전달로 같은 취소 이벤트가 여러 번 와도
 * 재고가 중복 증가하면 안 된다. → 항목(itemId) 단위 처리 완료 키로 1회만 복구.
 *
 * (StockDecreaseService와 동일한 Redis 분산 락 + 멱등 키 전략)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestockService {

    private static final String LOCK_KEY_PREFIX      = "stock:restock:lock:item:";
    private static final String PROCESSED_KEY_PREFIX = "stock:restocked:item:";
    private static final long   LOCK_WAIT_SECONDS    = 3L;
    private static final long   LOCK_LEASE_SECONDS   = 10L;
    private static final Duration PROCESSED_TTL      = Duration.ofDays(1);

    private final RedisTemplate<String, String>   redisTemplate;
    private final RedissonClient                  redissonClient;
    private final RestockTransactionService       restockTransactionService;

    public void restock(OrderItemCancelledPayload payload) {
        Long   itemId       = payload.itemId();
        String processedKey = PROCESSED_KEY_PREFIX + itemId;

        // 이미 복구된 항목이면 skip (중복 증가 방지)
        if (Boolean.TRUE.equals(redisTemplate.hasKey(processedKey))) {
            log.info("이미 재고 복구된 항목 — skip. itemId={}", itemId);
            return;
        }

        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + itemId);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new IllegalStateException("재고 복구 락 획득 실패. itemId=" + itemId);
            }
            // 락 내부 이중 체크
            if (Boolean.TRUE.equals(redisTemplate.hasKey(processedKey))) {
                log.info("이미 재고 복구된 항목 (락 내부) — skip. itemId={}", itemId);
                return;
            }

            restockTransactionService.increaseStock(payload.productId(), payload.quantity());
            redisTemplate.opsForValue().set(processedKey, "1", PROCESSED_TTL);

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("재고 복구 락 대기 중 인터럽트. itemId=" + itemId);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
