package com.ecommerce.order.controller;

import com.ecommerce.common.exception.GlobalExceptionHandler;
import com.ecommerce.order.dto.request.CartAddRequest;
import com.ecommerce.order.dto.response.CartItemResponse;
import com.ecommerce.order.dto.response.CartResponse;
import com.ecommerce.order.exception.CartItemNotFoundException;
import com.ecommerce.order.exception.OrderExceptionHandler;
import com.ecommerce.order.exception.ProductNotFoundException;
import com.ecommerce.order.service.CartService;
import com.ecommerce.order.support.CartPrincipal;
import com.ecommerce.order.support.CartPrincipalResolver;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartController 단위 테스트")
class CartControllerTest {

    private static final String CART_URL     = "/api/v1/cart";
    private static final String GUEST_ID     = "11111111-1111-4111-8111-111111111111";
    private static final int    MAX_QUANTITY = 999;

    @InjectMocks private CartController cartController;
    @Mock        private CartService    cartService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(cartController)
                .setControllerAdvice(new OrderExceptionHandler(), new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CartPrincipalResolver())
                .build();
    }

    // ── 조회 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET 조회 — 200 + 합계 응답")
    void getCart_ok() throws Exception {
        given(cartService.getCart(any(CartPrincipal.class))).willReturn(
                CartResponse.of(List.of(new CartItemResponse(10L, "테스트 상품", 1_000L, 2, null))));

        mockMvc.perform(get(CART_URL).header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value(10))
                .andExpect(jsonPath("$.totalPrice").value(2000))
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    @DisplayName("GET 조회 — X-User-Id 헤더 사용자로 격리 조회")
    void getCart_isolatedByHeader() throws Exception {
        given(cartService.getCart(any(CartPrincipal.class))).willReturn(CartResponse.of(List.of()));

        mockMvc.perform(get(CART_URL).header("X-User-Id", "7"))
                .andExpect(status().isOk());

        ArgumentCaptor<CartPrincipal> captor = ArgumentCaptor.forClass(CartPrincipal.class);
        then(cartService).should().getCart(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("GET 조회 — 비로그인은 guestId 쿠키로 게스트 장바구니 조회")
    void getCart_guestCookie() throws Exception {
        given(cartService.getCart(any(CartPrincipal.class))).willReturn(CartResponse.of(List.of()));

        mockMvc.perform(get(CART_URL).cookie(new Cookie("guestId", GUEST_ID)))
                .andExpect(status().isOk());

        ArgumentCaptor<CartPrincipal> captor = ArgumentCaptor.forClass(CartPrincipal.class);
        then(cartService).should().getCart(captor.capture());
        assertThat(captor.getValue().userId()).isNull();
        assertThat(captor.getValue().guestId()).isEqualTo(GUEST_ID);
    }

    // ── 담기 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST 담기 — 201")
    void addItem_created() throws Exception {
        mockMvc.perform(post(CART_URL + "/items")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":10,\"quantity\":2}"))
                .andExpect(status().isCreated());

        then(cartService).should().addItem(any(CartPrincipal.class), eq(new CartAddRequest(10L, 2)));
    }

    @Test
    @DisplayName("POST 담기 — 수량 1 미만이면 400 (서비스 미호출)")
    void addItem_belowMinQuantity() throws Exception {
        mockMvc.perform(post(CART_URL + "/items")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":10,\"quantity\":0}"))
                .andExpect(status().isBadRequest());

        then(cartService).should(never()).addItem(any(CartPrincipal.class), any(CartAddRequest.class));
    }

    @Test
    @DisplayName("POST 담기 — productId 누락이면 400")
    void addItem_missingProductId() throws Exception {
        mockMvc.perform(post(CART_URL + "/items")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST 담기 — 존재하지 않는 상품이면 404")
    void addItem_productNotFound() throws Exception {
        willThrow(new ProductNotFoundException(99L))
                .given(cartService).addItem(any(CartPrincipal.class), any(CartAddRequest.class));

        mockMvc.perform(post(CART_URL + "/items")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":99,\"quantity\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Product Not Found"));
    }

    // ── 수량 변경 ─────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH 수량 변경 — 200")
    void updateItem_ok() throws Exception {
        mockMvc.perform(patch(CART_URL + "/items/10")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":5}"))
                .andExpect(status().isOk());

        then(cartService).should().updateItem(any(CartPrincipal.class), eq(10L), eq(5));
    }

    @Test
    @DisplayName("PATCH 수량 변경 — 수량 1 미만이면 400 (서비스 미호출)")
    void updateItem_belowMinQuantity() throws Exception {
        mockMvc.perform(patch(CART_URL + "/items/10")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest());

        then(cartService).should(never()).updateItem(any(CartPrincipal.class), anyLong(), anyInt());
    }

    @Test
    @DisplayName("PATCH 수량 변경 — 상한(999) 초과면 400")
    void updateItem_aboveMaxQuantity() throws Exception {
        willThrow(new IllegalArgumentException("수량은 1 ~ " + MAX_QUANTITY + " 사이여야 합니다."))
                .given(cartService).updateItem(any(CartPrincipal.class), eq(10L), eq(MAX_QUANTITY + 1));

        mockMvc.perform(patch(CART_URL + "/items/10")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":" + (MAX_QUANTITY + 1) + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH 수량 변경 — 장바구니에 없는 상품이면 404")
    void updateItem_notFound() throws Exception {
        willThrow(new CartItemNotFoundException(99L))
                .given(cartService).updateItem(any(CartPrincipal.class), eq(99L), eq(1));

        mockMvc.perform(patch(CART_URL + "/items/99")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Cart Item Not Found"));
    }

    // ── 삭제 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE 개별 삭제 — 204")
    void removeItem_noContent() throws Exception {
        mockMvc.perform(delete(CART_URL + "/items/10").header("X-User-Id", "1"))
                .andExpect(status().isNoContent());

        then(cartService).should().removeItem(any(CartPrincipal.class), eq(10L));
    }

    @Test
    @DisplayName("DELETE 전체 비우기 — 204")
    void clearCart_noContent() throws Exception {
        mockMvc.perform(delete(CART_URL).header("X-User-Id", "1"))
                .andExpect(status().isNoContent());

        then(cartService).should().clearCart(any(CartPrincipal.class));
    }

    // ── 게스트 카트 병합 / 발급 ────────────────────────────────────

    @Test
    @DisplayName("POST 병합 — 미인증이면 401 (서비스 미호출)")
    void mergeGuestCart_unauthorized() throws Exception {
        mockMvc.perform(post(CART_URL + "/merge").cookie(new Cookie("guestId", GUEST_ID)))
                .andExpect(status().isUnauthorized());

        then(cartService).should(never()).mergeGuestCart(anyLong(), anyString());
    }

    @Test
    @DisplayName("POST 병합 — 인증 + guestId면 병합 후 쿠키 만료")
    void mergeGuestCart_ok() throws Exception {
        mockMvc.perform(post(CART_URL + "/merge")
                        .header("X-User-Id", "1")
                        .cookie(new Cookie("guestId", GUEST_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("guestId=;")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")));

        then(cartService).should().mergeGuestCart(1L, GUEST_ID);
    }

    @Test
    @DisplayName("POST 병합 — guestId 쿠키가 없으면 병합 없이 200")
    void mergeGuestCart_withoutGuestId() throws Exception {
        mockMvc.perform(post(CART_URL + "/merge").header("X-User-Id", "1"))
                .andExpect(status().isOk());

        then(cartService).should(never()).mergeGuestCart(anyLong(), anyString());
    }

    @Test
    @DisplayName("POST 게스트 발급 — HttpOnly 쿠키 신규 발급")
    void initGuestId_issuesCookie() throws Exception {
        mockMvc.perform(post(CART_URL + "/guest/init"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("guestId=")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")));
    }

    @Test
    @DisplayName("POST 게스트 발급 — 쿠키가 이미 있으면 재발급하지 않음")
    void initGuestId_keepsExistingCookie() throws Exception {
        mockMvc.perform(post(CART_URL + "/guest/init").cookie(new Cookie("guestId", GUEST_ID)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }
}
