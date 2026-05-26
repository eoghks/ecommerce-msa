package com.ecommerce.product.service;

import com.ecommerce.product.domain.Product;
import com.ecommerce.product.event.OrderCreatedPayload;
import com.ecommerce.product.event.StockEventPublisher;
import com.ecommerce.product.repository.ProductRepository;
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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockDecreaseService 단위 테스트")
class StockDecreaseServiceTest {

    @InjectMocks
    private StockDecreaseService stockDecreaseService;

    @Mock private ProductRepository              productRepository;
    @Mock private RedisTemplate<String, String>  redisTemplate;
    @Mock private RedissonClient                 redissonClient;
    @Mock private StockEventPublisher            stockEventPublisher;
    @Mock private RLock                          rLock;
    @Mock private ValueOperations<String, String> valueOps;

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
        Product product = buildProduct(10L, 10);

        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), anyString(), any())).willReturn(true);
        given(productRepository.findById(10L)).willReturn(Optional.of(product));

        // when
        stockDecreaseService.decreaseStock(payload);

        // then
        then(stockEventPublisher).should(times(1)).publishStockDecreased(1L);
        then(stockEventPublisher).should(never()).publishStockDecreaseFailed(anyLong(), anyString());
    }

    // ── 멱등성 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("이미 처리된 주문 이벤트 — 재고 차감 skip")
    void decreaseStock_alreadyProcessed_skip() throws InterruptedException {
        // given
        OrderCreatedPayload payload = buildPayload(1L, 10L, 2);

        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), anyString(), any())).willReturn(false); // 이미 처리됨

        // when
        stockDecreaseService.decreaseStock(payload);

        // then — 이벤트 발행 없음
        then(productRepository).should(never()).findById(anyLong());
        then(stockEventPublisher).should(never()).publishStockDecreased(anyLong());
        then(stockEventPublisher).should(never()).publishStockDecreaseFailed(anyLong(), anyString());
    }

    // ── 재고 부족 ───────────────────────────────────────────────────

    @Test
    @DisplayName("재고 부족 — stock.decrease.failed 이벤트 발행")
    void decreaseStock_insufficientStock_publishFailed() throws InterruptedException {
        // given
        OrderCreatedPayload payload = buildPayload(1L, 10L, 99);  // 재고(5)보다 많은 수량
        Product product = buildProduct(10L, 5);

        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), anyString(), any())).willReturn(true);
        given(productRepository.findById(10L)).willReturn(Optional.of(product));

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

        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(false);

        // when
        stockDecreaseService.decreaseStock(payload);

        // then
        then(stockEventPublisher).should(times(1))
                .publishStockDecreaseFailed(eq(1L), anyString());
        then(stockEventPublisher).should(never()).publishStockDecreased(anyLong());
    }

    // ── helper ──────────────────────────────────────────────────────

    private Product buildProduct(Long id, int stock) {
        return Product.builder()
                .name("테스트상품")
                .description("설명")
                .price(10_000L)
                .stock(stock)
                .imageUrl(null)
                .category(null)
                .build();
    }
}
