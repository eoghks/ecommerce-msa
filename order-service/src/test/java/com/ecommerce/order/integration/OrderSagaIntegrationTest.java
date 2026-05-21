package com.ecommerce.order.integration;

import com.ecommerce.common.event.StockDecreaseFailedEvent;
import com.ecommerce.common.event.StockDecreasedEvent;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Saga Choreography 통합 테스트 — Order Service.
 *
 * 검증 시나리오:
 *   1. stock.decreased 수신 → 주문 CONFIRMED
 *   2. stock.decrease.failed 수신 → 주문 CANCELLED (보상 트랜잭션)
 *
 * 인프라: Testcontainers(Postgres + Redis) + EmbeddedKafka
 */
@SpringBootTest
@Testcontainers
@DirtiesContext
@EmbeddedKafka(
        partitions = 1,
        topics = {"stock.decreased", "stock.decrease.failed", "order.created"}
)
class OrderSagaIntegrationTest {

    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7.2-alpine").withExposedPorts(6379);

    static {
        postgres.start();
        redis.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host",     redis::getHost);
        registry.add("spring.data.redis.port",     () -> redis.getMappedPort(6379));
        // EmbeddedKafka 브로커 주소 주입
        registry.add("spring.kafka.bootstrap-servers",
                () -> System.getProperty("spring.embedded.kafka.brokers"));
    }

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private OrderRepository orderRepository;

    // ── 시나리오 1: 재고 차감 성공 → CONFIRMED ───────────────────────

    @Test
    @DisplayName("stock.decreased 수신 → 주문 상태 CONFIRMED")
    void onStockDecreased_orderConfirmed() {
        // given — PENDING 주문 저장
        Order order = createPendingOrder(1L);
        Long orderId = orderRepository.save(order).getId();

        // when — stock.decreased 이벤트 발행
        kafkaTemplate.send("stock.decreased", String.valueOf(orderId),
                new StockDecreasedEvent(orderId));

        // then — 비동기 처리 대기 (최대 10초)
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    Order updated = orderRepository.findById(orderId).orElseThrow();
                    assertThat(updated.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
                });
    }

    // ── 시나리오 2: 재고 차감 실패 → CANCELLED (보상 트랜잭션) ──────

    @Test
    @DisplayName("stock.decrease.failed 수신 → 주문 상태 CANCELLED")
    void onStockDecreaseFailed_orderCancelled() {
        // given
        Order order = createPendingOrder(2L);
        Long orderId = orderRepository.save(order).getId();

        // when
        kafkaTemplate.send("stock.decrease.failed", String.valueOf(orderId),
                new StockDecreaseFailedEvent(orderId, "재고 부족"));

        // then
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    Order updated = orderRepository.findById(orderId).orElseThrow();
                    assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
                });
    }

    // ── helper ───────────────────────────────────────────────────────

    private Order createPendingOrder(Long userId) {
        OrderItem item = OrderItem.builder()
                .productId(10L)
                .productName("테스트상품")
                .price(10_000L)
                .quantity(1)
                .build();
        return Order.builder()
                .userId(userId)
                .totalPrice(10_000L)
                .items(List.of(item))
                .build();
    }
}
