package com.ecommerce.order.controller;

import com.ecommerce.order.domain.DeliveryStatus;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.dto.response.OrderResponse;
import com.ecommerce.order.exception.OrderExceptionHandler;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.exception.PurchaseAlreadyConfirmedException;
import com.ecommerce.order.exception.PurchaseConfirmNotAllowedException;
import com.ecommerce.order.exception.UnauthorizedException;
import com.ecommerce.order.service.OrderService;
import com.ecommerce.order.service.PurchaseConfirmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("구매확정 API 단위 테스트 (payment-foundation 3.2)")
class OrderPurchaseConfirmControllerTest {

    private static final String CONFIRM_URL = "/api/v1/orders/1/purchase-confirm";

    @InjectMocks private OrderController         orderController;
    @Mock        private OrderService            orderService;
    @Mock        private PurchaseConfirmService  purchaseConfirmService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orderController)
                .setControllerAdvice(new OrderExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("PATCH 구매확정 — 200 + 확정 시각 응답")
    void confirmPurchase_ok() throws Exception {
        given(purchaseConfirmService.confirm(1L, 5L)).willReturn(confirmedResponse());

        mockMvc.perform(patch(CONFIRM_URL).header("X-User-Id", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchaseConfirmedAt").exists())
                .andExpect(jsonPath("$.payableAmount").value(20000));
    }

    @Test
    @DisplayName("PATCH 구매확정 — 배송완료 전 주문 → 400 + 사유 메시지")
    void confirmPurchase_notDelivered_badRequest() throws Exception {
        given(purchaseConfirmService.confirm(1L, 5L)).willThrow(
                new PurchaseConfirmNotAllowedException("배송 완료된 주문만 구매확정할 수 있습니다."));

        mockMvc.perform(patch(CONFIRM_URL).header("X-User-Id", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("배송 완료된 주문만 구매확정할 수 있습니다."));
    }

    @Test
    @DisplayName("PATCH 구매확정 — 이미 확정된 주문 → 409")
    void confirmPurchase_alreadyConfirmed_conflict() throws Exception {
        given(purchaseConfirmService.confirm(1L, 5L))
                .willThrow(new PurchaseAlreadyConfirmedException(1L));

        mockMvc.perform(patch(CONFIRM_URL).header("X-User-Id", "5"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PATCH 구매확정 — 타인 주문 → 404")
    void confirmPurchase_otherUserOrder_notFound() throws Exception {
        given(purchaseConfirmService.confirm(1L, 5L)).willThrow(new OrderNotFoundException(1L));

        mockMvc.perform(patch(CONFIRM_URL).header("X-User-Id", "5"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH 구매확정 — X-User-Id 없음(미인증) → 401")
    void confirmPurchase_noUserHeader_unauthorized() throws Exception {
        given(purchaseConfirmService.confirm(1L, null))
                .willThrow(new UnauthorizedException("인증이 필요합니다."));

        mockMvc.perform(patch(CONFIRM_URL))
                .andExpect(status().isUnauthorized());
    }

    /** 구매확정된 주문 응답 */
    private OrderResponse confirmedResponse() {
        LocalDateTime now = LocalDateTime.now();
        return new OrderResponse(1L, 5L, OrderStatus.CONFIRMED, DeliveryStatus.DELIVERED,
                20_000L, 20_000L, 0L, 0L, 20_000L, 0L,
                now.minusDays(1), now, List.of(),
                "홍길동", "010-1234-5678", "서울시 강남구", now.minusDays(3), now);
    }
}
