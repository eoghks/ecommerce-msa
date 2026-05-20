package com.ecommerce.product.service;

import com.ecommerce.product.event.OrderCreatedPayload;
import com.ecommerce.product.event.StockEventPublisher;
import com.ecommerce.product.exception.ProductNotFoundException;
import com.ecommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 재고 차감 서비스.
 *
 * Redis Lock 전략:
 *   - 락 키: "stock:lock:order:{orderId}" — 동일 주문 이벤트 중복 처리 방지
 *   - 멱등성 키: "stock:processed:{orderId}" — at-least-once 재전달 시 중복 차감 방지
 *
 * 면접 포인트:
 *   - 재고는 상품별 락이 이상적이나, 동일 orderId 중복 처리 방지가 핵심이므로 orderId 기준 락 사용
 *   - 상품별 동시 차감 경쟁은 DB 트랜잭션 + 낙관적 락 or 비관적 락으로 추가 대응 가능
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

    private final ProductRepository    productRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final RedissonClient       redissonClient;
    private final StockEventPublisher  stockEventPublisher;

    /**
     * Redis 분산 락 + 멱등성 체크 후 재고 차감.
     * 성공 시 stock.decreased, 실패 시 stock.decrease.failed 발행.
     */
    public void decreaseStock(OrderCreatedPayload payload) {
        Long orderId  = payload.orderId();
        String lockKey      = LOCK_KEY_PREFIX + orderId;
        String processedKey = PROCESSED_KEY_PREFIX + orderId;

        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (!lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
                log.warn("재고 락 획득 실패 — 재시도 대기. orderId={}", orderId);
                throw new IllegalStateException("재고 락 획득 실패. orderId=" + orderId);
            }

            // 멱등성 체크 — 이미 처리된 주문이면 skip
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(processedKey, "1", PROCESSED_TTL);
            if (Boolean.FALSE.equals(isNew)) {
                log.info("이미 처리된 주문 이벤트 — skip. orderId={}", orderId);
                return;
            }

            // 실제 재고 차감
            doDecreaseStock(payload);

        } catch (IllegalStateException ex) {
            // 재고 부족 or 락 실패 — 보상 이벤트 발행
            redisTemplate.delete(processedKey);  // 처리 실패 시 멱등성 키 제거 (재시도 허용)
            stockEventPublisher.publishStockDecreaseFailed(orderId, ex.getMessage());
            return;

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            redisTemplate.delete(processedKey);
            stockEventPublisher.publishStockDecreaseFailed(orderId, "락 대기 중 인터럽트 발생");
            return;

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        stockEventPublisher.publishStockDecreased(orderId);
    }

    /**
     * 실제 재고 차감 — 트랜잭션 내에서 수행.
     * 재고 부족 시 IllegalStateException 발생 → 호출부에서 보상 처리.
     */
    @Transactional
    protected void doDecreaseStock(OrderCreatedPayload payload) {
        for (OrderCreatedPayload.Item item : payload.items()) {
            com.ecommerce.product.domain.Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new ProductNotFoundException(item.productId()));
            product.decreaseStock(item.quantity());  // 재고 부족 시 IllegalStateException
        }
        log.info("재고 차감 완료. orderId={}", payload.orderId());
    }
}
