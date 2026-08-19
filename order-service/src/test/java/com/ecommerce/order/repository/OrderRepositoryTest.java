package com.ecommerce.order.repository;

import com.ecommerce.order.config.JpaConfig;
import com.ecommerce.order.domain.DeliveryStatus;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderItemStatus;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.domain.ReturnRequest;
import com.ecommerce.order.domain.ReturnStatus;
import com.ecommerce.order.dto.AutoConfirmTarget;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@DisplayName("OrderRepository 구매 인증 조회 통합 테스트")
class OrderRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private OrderRepository orderRepository;
    @Autowired private TestEntityManager testEntityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    /** F-04: 목록 1회 + 항목 배치 1회 + count 1회 — 페이지 크기와 무관한 상한 */
    private static final int MAX_LIST_QUERY_COUNT = 3;
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

    // ── F-04: 주문 목록 N+1 회귀 방지 ─────────────────────────

    @Test
    @DisplayName("주문 목록 조회 쿼리 수는 페이지 크기에 비례하지 않는다")
    void findByUserId_doesNotTriggerNPlusOne() {
        int orderCount = 5;
        for (int i = 0; i < orderCount; i++) {
            saveOrderWith(USER_ID, buildItem(PRODUCT_ID + i, false), OrderStatus.CONFIRMED);
        }
        testEntityManager.flush();
        testEntityManager.clear();

        Statistics statistics = statistics();
        statistics.clear();

        Page<Order> orders = orderRepository.findByUserId(USER_ID, PageRequest.of(0, orderCount));
        orders.getContent().forEach(order -> order.getItems().size());

        assertThat(orders.getContent()).hasSize(orderCount);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(MAX_LIST_QUERY_COUNT);
    }

    // ── 구매확정 자격 쿼리 (payment-foundation §3.2, 리뷰 H-1·H-2·H-3) ──

    @Test
    @DisplayName("자동확정 대상(H-1) — 배송완료·미확정·기준일 경과 주문은 조회된다")
    void findAutoConfirmTargets_eligible() {
        Order order = saveDeliveredOrder(OrderStatus.CONFIRMED);

        List<AutoConfirmTarget> targets = findTargets();

        assertThat(targets).extracting(AutoConfirmTarget::orderId).containsExactly(order.getId());
    }

    @Test
    @DisplayName("자동확정 대상(H-1) — 전체 취소(CANCELLED)된 배송완료 주문은 제외된다")
    void findAutoConfirmTargets_cancelledOrder_excluded() {
        Order order = saveDeliveredOrder(OrderStatus.CANCELLED);

        assertThat(findTargets()).isEmpty();
        assertThat(confirmIfEligible(order.getId())).isZero();
    }

    @Test
    @DisplayName("자동확정 대상(H-2) — 진행 중(REQUESTED) 반품이 걸린 주문은 제외된다")
    void findAutoConfirmTargets_pendingReturn_excluded() {
        Order order = saveDeliveredOrder(OrderStatus.CONFIRMED);
        saveReturnRequest(order);

        assertThat(findTargets()).isEmpty();
        assertThat(confirmIfEligible(order.getId())).isZero();
    }

    @Test
    @DisplayName("자동확정(H-3) — 조건부 UPDATE 는 첫 실행만 1건, 재실행은 0건(중복 확정 차단)")
    void confirmPurchaseIfEligible_onlyOnce() {
        Order order = saveDeliveredOrder(OrderStatus.CONFIRMED);

        assertThat(confirmIfEligible(order.getId())).isEqualTo(1);
        assertThat(confirmIfEligible(order.getId())).isZero();
        assertThat(reload(order).getPurchaseConfirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("수동확정(H-3) — 조건부 UPDATE 는 동시 요청 중 1건만 성공한다")
    void confirmPurchaseNow_onlyOnce() {
        Order order = saveDeliveredOrder(OrderStatus.CONFIRMED);

        assertThat(confirmNow(order.getId())).isEqualTo(1);
        assertThat(confirmNow(order.getId())).isZero();
    }

    @Test
    @DisplayName("수동확정(H-1·H-2) — 취소된 주문·진행 중 반품이 있는 주문은 갱신 0건")
    void confirmPurchaseNow_ineligible() {
        Order cancelled = saveDeliveredOrder(OrderStatus.CANCELLED);
        Order withReturn = saveDeliveredOrder(OrderStatus.CONFIRMED);
        saveReturnRequest(withReturn);

        assertThat(confirmNow(cancelled.getId())).isZero();
        assertThat(confirmNow(withReturn.getId())).isZero();
    }

    // ── helpers ──────────────────────────────────────────────

    private List<AutoConfirmTarget> findTargets() {
        return orderRepository.findAutoConfirmTargets(DeliveryStatus.DELIVERED,
                OrderStatus.confirmableStatuses(), ReturnStatus.pendingStatuses(),
                LocalDateTime.now(), PageRequest.of(0, 100));
    }

    private int confirmIfEligible(Long orderId) {
        return orderRepository.confirmPurchaseIfEligible(orderId, DeliveryStatus.DELIVERED,
                OrderStatus.confirmableStatuses(), ReturnStatus.pendingStatuses(),
                LocalDateTime.now(), LocalDateTime.now());
    }

    private int confirmNow(Long orderId) {
        return orderRepository.confirmPurchaseNow(orderId, DeliveryStatus.DELIVERED,
                OrderStatus.confirmableStatuses(), ReturnStatus.pendingStatuses(),
                LocalDateTime.now());
    }

    private Order reload(Order order) {
        testEntityManager.flush();
        testEntityManager.clear();
        return orderRepository.findById(order.getId()).orElseThrow();
    }

    /**
     * 배송완료 주문 저장 — 기준일 경과 상태를 만들기 위해 배송완료 시각을 과거로 앞당긴다.
     * CANCELLED 는 전 항목 취소(반품 승인 경로)로 만들어 배송상태가 DELIVERED 로 남는 상황을 재현한다.
     */
    private Order saveDeliveredOrder(OrderStatus status) {
        OrderItem item = buildItem(PRODUCT_ID, false);
        Order order = Order.builder()
                .userId(USER_ID)
                .totalPrice(item.subtotal())
                .receiver("홍길동")
                .phone("010-1234-5678")
                .address("서울시 강남구")
                .items(List.of(item))
                .build();
        order.confirm();
        order.advanceDeliveryStatus(DeliveryStatus.SHIPPING);
        order.advanceDeliveryStatus(DeliveryStatus.DELIVERED);
        Order saved = orderRepository.saveAndFlush(order);
        if (status == OrderStatus.CANCELLED) {
            saved.cancelItem(saved.getItems().get(0).getId(), "반품 승인");
        }
        ReflectionTestUtils.setField(saved, "deliveredAt", LocalDateTime.now().minusDays(8));
        return orderRepository.saveAndFlush(saved);
    }

    /** 진행 중(REQUESTED) 반품 1건 저장 */
    private void saveReturnRequest(Order order) {
        testEntityManager.persistAndFlush(ReturnRequest.builder()
                .orderId(order.getId())
                .orderItemId(order.getItems().get(0).getId())
                .userId(order.getUserId())
                .reason("제품 하자")
                .build());
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

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
