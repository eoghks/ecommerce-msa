package com.ecommerce.order.service;

import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.domain.Payment;
import com.ecommerce.order.domain.PaymentProvider;
import com.ecommerce.order.dto.ShippingInfo;
import com.ecommerce.order.dto.response.OrderResponse;
import com.ecommerce.order.event.OrderCreatedApplicationEvent;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPersistenceService 단위 테스트")
class OrderPersistenceServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks private OrderPersistenceService orderPersistenceService;

    private static final ShippingInfo SHIPPING = new ShippingInfo("홍길동", "010", "주소");

    @Test
    @DisplayName("V1.1-6: 결제금액이 남은 주문은 PAYMENT_PENDING 으로 저장되고 order.created 를 발행하지 않는다")
    void saveAndPublish_paymentPending_noEvent() {
        OrderItem item = item(1000L, 2);
        Order saved = order(item, 2000L);
        given(orderRepository.save(any(Order.class))).willReturn(saved);

        OrderResponse response = orderPersistenceService.saveAndPublish(1L, 2000L, List.of(item), SHIPPING);

        assertThat(response.status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        // 승인 전에는 재고를 차감하지 않는다 (payment-foundation §5)
        then(applicationEventPublisher).should(never())
                .publishEvent(any(OrderCreatedApplicationEvent.class));
        then(paymentRepository).should(never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("V1.1-6(§11-3): payable=0 이면 PG 호출 없이 즉시 PENDING + order.created 발행")
    void saveAndPublish_zeroPayable_settlesImmediately() {
        OrderItem item = item(0L, 1);
        Order saved = order(item, 0L);
        given(orderRepository.save(any(Order.class))).willReturn(saved);
        given(paymentRepository.save(any(Payment.class))).willAnswer(call -> call.getArgument(0));

        OrderResponse response = orderPersistenceService.saveAndPublish(1L, 0L, List.of(item), SHIPPING);

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        // 내부 결제 레코드는 남기되 PG 왕복은 하지 않는다
        then(paymentRepository).should(times(1)).save(any(Payment.class));
        then(applicationEventPublisher).should(times(1))
                .publishEvent(any(OrderCreatedApplicationEvent.class));
    }

    @Test
    @DisplayName("V1.1-6(§11-3): 0원 결제 레코드는 PG 제공사 NONE + 승인 완료로 남는다")
    void saveAndPublish_zeroPayable_recordsInternalPayment() {
        OrderItem item = item(0L, 1);
        given(orderRepository.save(any(Order.class))).willReturn(order(item, 0L));
        given(paymentRepository.save(any(Payment.class))).willAnswer(call -> call.getArgument(0));

        orderPersistenceService.saveAndPublish(1L, 0L, List.of(item), SHIPPING);

        org.mockito.ArgumentCaptor<Payment> captor = org.mockito.ArgumentCaptor.forClass(Payment.class);
        then(paymentRepository).should().save(captor.capture());
        Payment payment = captor.getValue();
        assertThat(payment.getPgProvider()).isEqualTo(PaymentProvider.NONE);
        assertThat(payment.getAmount()).isZero();
        assertThat(payment.requiresPgCall()).isFalse();
    }

    private OrderItem item(Long price, int quantity) {
        return OrderItem.builder()
                .productId(10L).productName("상품").price(price).quantity(quantity).sellerId(7L).build();
    }

    private Order order(OrderItem item, Long totalPrice) {
        Order order = Order.builder().userId(1L).totalPrice(totalPrice).items(List.of(item)).build();
        ReflectionTestUtils.setField(order, "id", 1L);
        return order;
    }
}
