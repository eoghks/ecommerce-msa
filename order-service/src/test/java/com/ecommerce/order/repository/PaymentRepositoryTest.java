package com.ecommerce.order.repository;

import com.ecommerce.order.config.JpaConfig;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.Payment;
import com.ecommerce.order.domain.PaymentProvider;
import com.ecommerce.order.domain.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 결제 정합성 조회·락 통합 테스트 (H-02, C-01/C-02).
 * 실제 Postgres 에서 낙관적 락 충돌과 회수 대상 조회 조건을 검증한다.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("PaymentRepository 통합 테스트 (H-02/C-01)")
class PaymentRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Long USER_ID = 1L;
    private static final Long AMOUNT  = 20_000L;

    @Autowired private PaymentRepository  paymentRepository;
    @Autowired private TestEntityManager  testEntityManager;

    @Test
    @DisplayName("H-02 동시 부분환불 — 먼저 커밋된 갱신 뒤 낡은 버전으로 갱신하면 낙관적 락 충돌(409 대상)")
    void concurrentPartialCancel_throwsOptimisticLockingFailure() {
        Payment payment = savedApprovedPayment(orderId(), "ORD-00000001-aaaa");

        // 다른 트랜잭션이 먼저 부분환불을 반영한 상황을 재현한다(버전 증가)
        testEntityManager.getEntityManager().createQuery("""
                        update Payment p
                        set p.cancelledAmount = 5000, p.version = p.version + 1
                        where p.id = :id
                        """)
                .setParameter("id", payment.getId())
                .executeUpdate();

        // 낡은 버전을 들고 있는 트랜잭션이 같은 필드를 갱신하면 갱신 유실 대신 충돌로 실패해야 한다
        payment.applyCancel(5_000L, LocalDateTime.now());

        // 리포지토리 경유(예외 변환 적용) — 서비스 계층이 받는 예외이자 409 로 매핑되는 타입이다
        assertThatThrownBy(() -> paymentRepository.saveAndFlush(payment))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("H-02 취소용 조회 — 잠금 조회도 실패 건은 제외하고 유효 결제 1건을 반환한다")
    void findActiveByOrderIdForUpdate_returnsActivePayment() {
        Long orderId = orderId();
        savedApprovedPayment(orderId, "ORD-00000002-bbbb");

        assertThat(paymentRepository.findActiveByOrderIdForUpdate(orderId, PaymentStatus.FAILED))
                .isPresent();
    }

    @Test
    @DisplayName("C-01 회수 대상 — 기준 시각이 지난 진행 중·미확정 결제만 조회한다")
    void findStalePayments_filtersByStatusAndCreatedAt() {
        Long staleOrderId = orderId();
        Payment stale = savedReadyPayment(staleOrderId, "ORD-00000003-cccc");
        overrideCreatedAt(stale.getId(), LocalDateTime.now().minusMinutes(10));

        // 방금 만들어진 결제는 아직 정상 승인 중일 수 있어 회수 대상이 아니다
        savedReadyPayment(orderId(), "ORD-00000004-dddd");
        // 승인 완료 결제는 회수 대상이 아니다
        savedApprovedPayment(orderId(), "ORD-00000005-eeee");
        testEntityManager.clear();

        List<Payment> targets = paymentRepository.findStalePayments(
                PaymentStatus.inFlightStatuses(), LocalDateTime.now().minusMinutes(3),
                PageRequest.of(0, 10));

        assertThat(targets).hasSize(1);
        assertThat(targets.get(0).getOrderId()).isEqualTo(staleOrderId);
    }

    @Test
    @DisplayName("C-02 재결제 차단 해소 대상 — 본인 주문의 오래된 진행 중 결제만 조회한다")
    void findStaleByOrderId_filtersByOwner() {
        Long orderId = orderId();
        Payment stale = savedReadyPayment(orderId, "ORD-00000006-ffff");
        overrideCreatedAt(stale.getId(), LocalDateTime.now().minusMinutes(10));
        testEntityManager.clear();

        LocalDateTime threshold = LocalDateTime.now().minusMinutes(3);
        assertThat(paymentRepository.findStaleByOrderId(
                orderId, USER_ID, PaymentStatus.inFlightStatuses(), threshold)).hasSize(1);
        assertThat(paymentRepository.findStaleByOrderId(
                orderId, 999L, PaymentStatus.inFlightStatuses(), threshold)).isEmpty();
    }

    // ── helper ───────────────────────────────────────────────────

    /** 결제는 orders FK 를 참조하므로 주문을 먼저 만든다 */
    private Long orderId() {
        OrderItem item = OrderItem.builder()
                .productId(10L).productName("상품").price(AMOUNT).quantity(1).sellerId(3L).build();
        Order order = Order.builder()
                .userId(USER_ID).totalPrice(AMOUNT).receiver("수령인").phone("010-0000-0000")
                .address("서울").items(List.of(item)).build();
        return testEntityManager.persistAndFlush(order).getId();
    }

    private Payment savedReadyPayment(Long orderId, String pgOrderId) {
        return testEntityManager.persistAndFlush(Payment.builder()
                .orderId(orderId).userId(USER_ID).pgProvider(PaymentProvider.TOSS)
                .pgOrderId(pgOrderId).amount(AMOUNT).build());
    }

    /** 감사 필드(created_at)는 updatable=false 라 네이티브 UPDATE 로 덮는다 */
    private void overrideCreatedAt(Long paymentId, LocalDateTime createdAt) {
        testEntityManager.getEntityManager()
                .createNativeQuery("update payment set created_at = :createdAt where id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", paymentId)
                .executeUpdate();
    }

    private Payment savedApprovedPayment(Long orderId, String pgOrderId) {
        Payment payment = Payment.builder()
                .orderId(orderId).userId(USER_ID).pgProvider(PaymentProvider.TOSS)
                .pgOrderId(pgOrderId).amount(AMOUNT).build();
        payment.approve("test_payment_key_" + pgOrderId, LocalDateTime.now());
        return testEntityManager.persistAndFlush(payment);
    }
}
