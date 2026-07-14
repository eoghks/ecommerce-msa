package com.ecommerce.order.service;

import com.ecommerce.order.domain.OrderItemStatus;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.exception.InvalidInternalTokenException;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("InternalOrderService 단위 테스트")
class InternalOrderServiceTest {

    @Mock private OrderRepository orderRepository;

    @InjectMocks private InternalOrderService internalOrderService;

    // C1: 실구매 인정 상태 집합 (서비스 상수와 동일)
    private static final Set<OrderStatus> PURCHASED_STATUSES =
            Set.of(OrderStatus.CONFIRMED, OrderStatus.PARTIALLY_CANCELLED);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(internalOrderService, "internalToken", "dev-internal-secret");
    }

    @Test
    @DisplayName("구매 인증 — 일치하는 토큰이면 통과")
    void verifyInternalToken_valid() {
        internalOrderService.verifyInternalToken("dev-internal-secret");
    }

    @Test
    @DisplayName("구매 인증 — 토큰 null이면 403")
    void verifyInternalToken_null() {
        assertThatThrownBy(() -> internalOrderService.verifyInternalToken(null))
                .isInstanceOf(InvalidInternalTokenException.class);
    }

    @Test
    @DisplayName("구매 인증 — 토큰 불일치면 403")
    void verifyInternalToken_mismatch() {
        assertThatThrownBy(() -> internalOrderService.verifyInternalToken("wrong-token"))
                .isInstanceOf(InvalidInternalTokenException.class);
    }

    @Test
    @DisplayName("구매 판정 — ACTIVE 항목 보유 주문 있으면 true")
    void isPurchased_true() {
        given(orderRepository.existsPurchasedProduct(1L, 10L, OrderItemStatus.ACTIVE, PURCHASED_STATUSES))
                .willReturn(true);

        assertThat(internalOrderService.isPurchased(1L, 10L)).isTrue();
    }

    @Test
    @DisplayName("구매 판정 — 취소만 있거나 구매 없으면 false")
    void isPurchased_false() {
        given(orderRepository.existsPurchasedProduct(1L, 10L, OrderItemStatus.ACTIVE, PURCHASED_STATUSES))
                .willReturn(false);

        assertThat(internalOrderService.isPurchased(1L, 10L)).isFalse();
    }

    @Test
    @DisplayName("구매 판정 — userId/productId null이면 false (조회 없음)")
    void isPurchased_nullArgs() {
        assertThat(internalOrderService.isPurchased(null, 10L)).isFalse();
        assertThat(internalOrderService.isPurchased(1L, null)).isFalse();
    }
}
