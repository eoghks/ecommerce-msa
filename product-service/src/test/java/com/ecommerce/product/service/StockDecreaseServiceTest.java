package com.ecommerce.product.service;

import com.ecommerce.product.event.OrderCreatedPayload;
import com.ecommerce.product.event.StockEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockDecreaseService 단위 테스트")
class StockDecreaseServiceTest {

    @InjectMocks
    private StockDecreaseService stockDecreaseService;

    @Mock private StockDecreaseTransactionService  stockDecreaseTransactionService;
    @Mock private RedisTemplate<String, String>    redisTemplate;
    @Mock private RedissonClient                   redissonClient;
    @Mock private StockEventPublisher              stockEventPublisher;
    @Mock private RLock                            rLock;
    @Mock private ValueOperations<String, String>  valueOps;

    private OrderCreatedPayload buildPayload(Long orderId, Long productId, int quantity) {
        return new OrderCreatedPayload(
                orderId, 1L,
                List.of(new OrderCreatedPayload.Item(productId, quantity))
        );
    }

    // ── 정상 차감 ───────────────────────────────────────────────────

    @Test
    @DisplayName("재고 차감 성공 — stock.decreased 이벤트 발행")
    void decreaseStock_success() throws InterruptedException {
        // given
        OrderCreatedPayload payload = buildPayload(1L, 10L, 2);

        given(redisTemplate.hasKey(anyString())).willReturn(false);
        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);
        given(redisTemplate.opsForValue()).willReturn(valueOps); // 멱등성 키 저장용

        // when
        stockDecreaseService.decreaseStock(payload);

        // then
        then(stockDecreaseTransactionService).should(times(1)).decreaseStock(payload);
        then(stockEventPublisher).should(times(1)).publishStockDecreased(1L);
        then(stockEventPublisher).should(never()).publishStockDecreaseFailed(anyLong(), anyString());
    }

    // ── 멱등성 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("이미 처리된 주문 이벤트 — 재고 차감 skip")
    void decreaseStock_alreadyProcessed_skip() throws InterruptedException {
        // given
        OrderCreatedPayload payload = buildPayload(1L, 10L, 2);

        given(redisTemplate.hasKey(anyString())).willReturn(true); // 이미 처리됨

        // when
        stockDecreaseService.decreaseStock(payload);

        // then — 락 획득 없이 즉시 skip
        then(redissonClient).should(never()).getLock(anyString());
        then(stockDecreaseTransactionService).should(never()).decreaseStock(any());
        then(stockEventPublisher).should(never()).publishStockDecreased(anyLong());
        then(stockEventPublisher).should(never()).publishStockDecreaseFailed(anyLong(), anyString());
    }

    // ── 재고 부족 ───────────────────────────────────────────────────

    @Test
    @DisplayName("재고 부족 — stock.decrease.failed 이벤트 발행")
    void decreaseStock_insufficientStock_publishFailed() throws InterruptedException {
        // given
        OrderCreatedPayload payload = buildPayload(1L, 10L, 99);

        given(redisTemplate.hasKey(anyString())).willReturn(false);
        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);
        willThrow(new IllegalStateException("재고 부족"))
                .given(stockDecreaseTransactionService).decreaseStock(payload);

        // when
        stockDecreaseService.decreaseStock(payload);

        // then
        then(stockEventPublisher).should(times(1))
                .publishStockDecreaseFailed(eq(1L), anyString());
        then(stockEventPublisher).should(never()).publishStockDecreased(anyLong());
    }

    // ── 락 획득 실패 ────────────────────────────────────────────────

    @Test
    @DisplayName("Redis 락 획득 실패 — stock.decrease.failed 이벤트 발행")
    void decreaseStock_lockFailed_publishFailed() throws InterruptedException {
        // given
        OrderCreatedPayload payload = buildPayload(1L, 10L, 2);

        given(redisTemplate.hasKey(anyString())).willReturn(false);
        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(false);

        // when
        stockDecreaseService.decreaseStock(payload);

        // then
        then(stockDecreaseTransactionService).should(never()).decreaseStock(any());
        then(stockEventPublisher).should(times(1))
                .publishStockDecreaseFailed(eq(1L), anyString());
        then(stockEventPublisher).should(never()).publishStockDecreased(anyLong());
    }
}
