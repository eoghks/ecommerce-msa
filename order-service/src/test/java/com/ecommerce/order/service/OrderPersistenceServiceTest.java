package com.ecommerce.order.service;

import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.dto.ShippingInfo;
import com.ecommerce.order.dto.response.OrderResponse;
import com.ecommerce.order.event.OrderCreatedApplicationEvent;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPersistenceService 단위 테스트")
class OrderPersistenceServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks private OrderPersistenceService orderPersistenceService;

    @Test
    @DisplayName("저장 후 order.created 발행용 ApplicationEvent 등록 (AFTER_COMMIT)")
    void saveAndPublish_publishesEvent() {
        Long userId = 1L;
        ShippingInfo shipping = new ShippingInfo("홍길동", "010", "주소");

        OrderItem item = OrderItem.builder()
                .productId(10L).productName("상품").price(1000L).quantity(2).sellerId(7L).build();
        Order saved = Order.builder().userId(userId).totalPrice(2000L).items(List.of(item)).build();
        given(orderRepository.save(any(Order.class))).willReturn(saved);

        OrderResponse response = orderPersistenceService.saveAndPublish(userId, 2000L, List.of(item), shipping);

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        // 핵심: 별도 빈에서 이벤트가 등록되어야 AFTER_COMMIT 발행이 동작 (self-invocation 버그 회귀 방지)
        then(applicationEventPublisher).should(times(1))
                .publishEvent(any(OrderCreatedApplicationEvent.class));
    }
}
