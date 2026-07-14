package com.ecommerce.order.repository;

import com.ecommerce.order.config.JpaConfig;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderItemStatus;
import com.ecommerce.order.domain.OrderStatus;
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
import java.util.Set;

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
    // C1: 실제 재고가 차감된 실구매 상태만 인정
    private static final Set<OrderStatus> PURCHASED_STATUSES =
            Set.of(OrderStatus.CONFIRMED, OrderStatus.PARTIALLY_CANCELLED);

    @Test
    @DisplayName("CONFIRMED 주문의 ACTIVE 항목으로 구매한 이력이 있으면 true")
    void existsPurchasedProduct_confirmed_true() {
        saveOrderWith(USER_ID, buildItem(PRODUCT_ID, false), OrderStatus.CONFIRMED);

        boolean purchased = existsPurchased(USER_ID, PRODUCT_ID);

        assertThat(purchased).isTrue();
    }

    @Test
    @DisplayName("PENDING 주문(재고 미차감)은 실구매가 아니므로 false")
    void existsPurchasedProduct_pending_false() {
        saveOrderWith(USER_ID, buildItem(PRODUCT_ID, false), OrderStatus.PENDING);

        boolean purchased = existsPurchased(USER_ID, PRODUCT_ID);

        assertThat(purchased).isFalse();
    }

    @Test
    @DisplayName("PENDING에서 전체취소(CANCELLED)된 주문은 항목이 ACTIVE로 남아도 false")
    void existsPurchasedProduct_pendingCancelled_false() {
        saveOrderWith(USER_ID, buildItem(PRODUCT_ID, false), OrderStatus.CANCELLED);

        boolean purchased = existsPurchased(USER_ID, PRODUCT_ID);

        assertThat(purchased).isFalse();
    }

    @Test
    @DisplayName("해당 상품 항목이 취소(CANCELLED)만 있으면 false")
    void existsPurchasedProduct_itemCancelledOnly_false() {
        saveOrderWith(USER_ID, buildItem(PRODUCT_ID, true), OrderStatus.CONFIRMED);

        boolean purchased = existsPurchased(USER_ID, PRODUCT_ID);

        assertThat(purchased).isFalse();
    }

    @Test
    @DisplayName("해당 상품 구매 이력이 전혀 없으면 false")
    void existsPurchasedProduct_none_false() {
        saveOrderWith(USER_ID, buildItem(99L, false), OrderStatus.CONFIRMED);

        boolean purchased = existsPurchased(USER_ID, PRODUCT_ID);

        assertThat(purchased).isFalse();
    }

    @Test
    @DisplayName("다른 사용자의 구매 이력은 무관 — false")
    void existsPurchasedProduct_otherUser_false() {
        saveOrderWith(2L, buildItem(PRODUCT_ID, false), OrderStatus.CONFIRMED);

        boolean purchased = existsPurchased(USER_ID, PRODUCT_ID);

        assertThat(purchased).isFalse();
    }

    // ── helpers ──────────────────────────────────────────────

    private boolean existsPurchased(Long userId, Long productId) {
        return orderRepository.existsPurchasedProduct(
                userId, productId, OrderItemStatus.ACTIVE, PURCHASED_STATUSES);
    }

    private void saveOrderWith(Long userId, OrderItem item, OrderStatus status) {
        Order order = Order.builder()
                .userId(userId)
                .totalPrice(item.subtotal())
                .receiver("홍길동")
                .phone("010-1234-5678")
                .address("서울시 강남구")
                .items(List.of(item))
                .build();
        applyStatus(order, status);
        orderRepository.save(order);
    }

    /** 주문 상태를 도메인 전이 메서드로 세팅 (PENDING은 기본 상태) */
    private void applyStatus(Order order, OrderStatus status) {
        if (status == OrderStatus.CONFIRMED) {
            order.confirm();
        } else if (status == OrderStatus.CANCELLED) {
            order.cancel();
        }
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
