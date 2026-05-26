package com.ecommerce.product.integration;

import com.ecommerce.common.event.StockDecreaseFailedEvent;
import com.ecommerce.common.event.StockDecreasedEvent;
import com.ecommerce.product.domain.Category;
import com.ecommerce.product.domain.Product;
import com.ecommerce.product.event.OrderCreatedPayload;
import com.ecommerce.product.repository.CategoryRepository;
import com.ecommerce.product.repository.ProductRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Saga Choreography 통합 테스트 — Product Service.
 *
 * 검증 시나리오:
 *   1. order.created 수신 → 재고 차감 → stock.decreased 발행
 *   2. order.created 수신 (재고 부족) → stock.decrease.failed 발행
 *
 * 인프라: Testcontainers(Postgres + Redis) + EmbeddedKafka
 */
@SpringBootTest
@Testcontainers
@DirtiesContext
@EmbeddedKafka(
        partitions = 1,
        topics = {"order.created", "stock.decreased", "stock.decrease.failed"}
)
@Import(StockDecreaseSagaIntegrationTest.CaptureConfig.class)
class StockDecreaseSagaIntegrationTest {

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
        registry.add("spring.kafka.bootstrap-servers",
                () -> System.getProperty("spring.embedded.kafka.brokers"));
    }

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private ProductRepository  productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private StockEventCapture  stockEventCapture;

    // ── 시나리오 1: 재고 정상 차감 ────────────────────────────────────

    @Test
    @DisplayName("order.created 수신 → 재고 차감 → stock.decreased 발행")
    void onOrderCreated_stockDecreased() {
        Product product = saveProduct(10);
        Long productId = product.getId();

        kafkaTemplate.send("order.created", "100",
                new OrderCreatedPayload(100L, 1L,
                        List.of(new OrderCreatedPayload.Item(productId, 3))));

        // 재고 7개로 감소 확인
        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    Product updated = productRepository.findById(productId).orElseThrow();
                    assertThat(updated.getStock()).isEqualTo(7);
                });

        // stock.decreased 이벤트 발행 확인
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                        assertThat(stockEventCapture.getLastDecreasedOrderId()).isEqualTo(100L));
    }

    // ── 시나리오 2: 재고 부족 → 실패 이벤트 발행 ───────────────────────

    @Test
    @DisplayName("재고 부족 시 stock.decrease.failed 발행")
    void onOrderCreated_insufficientStock_failedEventPublished() {
        Product product = saveProduct(2);
        Long productId = product.getId();

        kafkaTemplate.send("order.created", "200",
                new OrderCreatedPayload(200L, 1L,
                        List.of(new OrderCreatedPayload.Item(productId, 5))));

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() ->
                        assertThat(stockEventCapture.getLastFailedOrderId()).isEqualTo(200L));

        // 재고 변경 없음 확인
        assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(2);
    }

    // ── helper ───────────────────────────────────────────────────────

    private Product saveProduct(int stock) {
        Category category = categoryRepository.save(
                Category.builder().name("카테고리-" + System.nanoTime()).build()
        );
        return productRepository.save(
                Product.builder()
                        .name("테스트상품")
                        .description("설명")
                        .price(10_000L)
                        .stock(stock)
                        .imageUrl(null)
                        .category(category)
                        .build()
        );
    }

    // ── 결과 이벤트 캡처 Bean ─────────────────────────────────────────

    /**
     * @TestConfiguration + @Import 로 명시적 Bean 등록.
     * @Component 로 선언 시 SpringBootTest 컴포넌트 스캔에 포함되지 않아 NoSuchBeanDefinitionException 발생.
     */
    @TestConfiguration
    static class CaptureConfig {
        @Bean
        public StockEventCapture stockEventCapture() {
            return new StockEventCapture();
        }
    }

    static class StockEventCapture {

        private final AtomicReference<Long> lastDecreasedOrderId = new AtomicReference<>();
        private final AtomicReference<Long> lastFailedOrderId    = new AtomicReference<>();

        @KafkaListener(topics = "stock.decreased",       groupId = "test-capture-decreased")
        public void onDecreased(StockDecreasedEvent event) {
            lastDecreasedOrderId.set(event.getOrderId());
        }

        @KafkaListener(topics = "stock.decrease.failed", groupId = "test-capture-failed")
        public void onFailed(StockDecreaseFailedEvent event) {
            lastFailedOrderId.set(event.getOrderId());
        }

        public Long getLastDecreasedOrderId() { return lastDecreasedOrderId.get(); }
        public Long getLastFailedOrderId()    { return lastFailedOrderId.get(); }
    }
}
