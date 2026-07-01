package com.ecommerce.product.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCreatedDltConsumer 단위 테스트")
class OrderCreatedDltConsumerTest {

    @Mock private StockEventPublisher stockEventPublisher;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @InjectMocks private OrderCreatedDltConsumer consumer;

    private OrderCreatedPayload payload() {
        return new OrderCreatedPayload(100L, 1L, List.of(new OrderCreatedPayload.Item(10L, 2)));
    }

    @Test
    @DisplayName("차감 안 됨(멱등키 없음) — 취소 보상(stock.decrease.failed) 발행")
    void consume_notDecreased_publishesFailed() {
        given(redisTemplate.hasKey("stock:processed:100")).willReturn(false);

        consumer.consume(payload());

        verify(stockEventPublisher).publishStockDecreaseFailed(eq(100L), contains("DLT"));
        verify(stockEventPublisher, never()).publishStockDecreased(eq(100L));
    }

    @Test
    @DisplayName("차감 완료됨(멱등키 존재) — 성공 재발행(stock.decreased), over-cancel 방지")
    void consume_alreadyDecreased_republishesSuccess() {
        given(redisTemplate.hasKey("stock:processed:100")).willReturn(true);

        consumer.consume(payload());

        verify(stockEventPublisher).publishStockDecreased(eq(100L));
        verify(stockEventPublisher, never()).publishStockDecreaseFailed(eq(100L), org.mockito.ArgumentMatchers.anyString());
    }
}
