package com.ecommerce.order.repository;

import com.ecommerce.order.config.JpaConfig;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderItemStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("OrderRepository 구매 인증 조회 통합 테스트")
class OrderRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private OrderRepository orderRepository;

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    @Test
    @DisplayName("ACTIVE 항목으로 구매한 이력이 있으면 true")
    void existsPurchasedProduct_active_true() {
        saveOrderWith(USER_ID, buildItem(PRODUCT_ID, false));

        boolean purchased = orderRepository.existsPurchasedProduct(USER_ID, PRODUCT_ID, OrderItemStatus.ACTIVE);

        assertThat(purchased).isTrue();
    }

    @Test
    @DisplayName("해당 상품 항목이 취소(CANCELLED)만 있으면 false")
    void existsPurchasedProduct_cancelledOnly_false() {
        saveOrderWith(USER_ID, buildItem(PRODUCT_ID, true));

        boolean purchased = orderRepository.existsPurchasedProduct(USER_ID, PRODUCT_ID, OrderItemStatus.ACTIVE);

        assertThat(purchased).isFalse();
    }

    @Test
    @DisplayName("해당 상품 구매 이력이 전혀 없으면 false")
    void existsPurchasedProduct_none_false() {
        saveOrderWith(USER_ID, buildItem(99L, false));

        boolean purchased = orderRepository.existsPurchasedProduct(USER_ID, PRODUCT_ID, OrderItemStatus.ACTIVE);

        assertThat(purchased).isFalse();
    }

    @Test
    @DisplayName("다른 사용자의 구매 이력은 무관 — false")
    void existsPurchasedProduct_otherUser_false() {
        saveOrderWith(2L, buildItem(PRODUCT_ID, false));

        boolean purchased = orderRepository.existsPurchasedProduct(USER_ID, PRODUCT_ID, OrderItemStatus.ACTIVE);

        assertThat(purchased).isFalse();
    }

    // ── helpers ──────────────────────────────────────────────

    private void saveOrderWith(Long userId, OrderItem item) {
        Order order = Order.builder()
                .userId(userId)
                .totalPrice(item.subtotal())
                .receiver("홍길동")
                .phone("010-1234-5678")
                .address("서울시 강남구")
                .items(List.of(item))
                .build();
        orderRepository.save(order);
    }

    private OrderItem buildItem(Long productId, boolean cancelled) {
        OrderItem item = OrderItem.builder()
                .productId(productId)
                .productName("테스트 상품")
                .price(1_000L)
                .quantity(1)
                .sellerId(7L)
                .build();
        if (cancelled) {
            item.cancel("취소 사유", java.time.LocalDateTime.now());
        }
        return item;
    }
}
