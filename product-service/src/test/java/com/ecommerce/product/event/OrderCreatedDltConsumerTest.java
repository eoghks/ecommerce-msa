package com.ecommerce.product.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCreatedDltConsumer 단위 테스트")
class OrderCreatedDltConsumerTest {

    @Mock private StockEventPublisher stockEventPublisher;
    @InjectMocks private OrderCreatedDltConsumer consumer;

    @Test
    @DisplayName("DLT 수신 시 stock.decrease.failed 보상 이벤트 발행 → 주문 취소 유도")
    void consume_publishesFailedCompensation() {
        OrderCreatedPayload payload = new OrderCreatedPayload(
                100L, 1L, List.of(new OrderCreatedPayload.Item(10L, 2)));

        consumer.consume(payload);

        verify(stockEventPublisher).publishStockDecreaseFailed(eq(100L), contains("DLT"));
    }
}
