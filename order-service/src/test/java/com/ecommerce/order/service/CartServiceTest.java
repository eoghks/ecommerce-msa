package com.ecommerce.order.service;

import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.config.RedisJsonMapper;
import com.ecommerce.order.domain.CartItem;
import com.ecommerce.order.dto.request.CartAddRequest;
import com.ecommerce.order.dto.response.CartResponse;
import com.ecommerce.order.exception.CartItemNotFoundException;
import com.ecommerce.order.exception.ProductNotFoundException;
import com.ecommerce.order.repository.CartItemRepository;
import com.ecommerce.order.support.CartPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartService 단위 테스트")
class CartServiceTest {

    private static final long   USER_ID       = 1L;
    private static final long   OTHER_USER_ID = 2L;
    private static final String GUEST_ID      = "11111111-1111-4111-8111-111111111111";
    private static final String GUEST_KEY     = "cart:guest:" + GUEST_ID;
    private static final int    MAX_QUANTITY  = 999;

    @Mock private CartItemRepository              cartItemRepository;
    @Mock private RedisTemplate<String, String>   redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ProductClient                   productClient;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        // 직렬화 형식까지 함께 검증하기 위해 실제 RedisJsonMapper 사용
        cartService = new CartService(cartItemRepository, redisTemplate, new RedisJsonMapper(), productClient);
    }

    // ── 로그인 사용자 (DB) ─────────────────────────────────────────

    @Test
    @DisplayName("조회 — 로그인 사용자는 DB 항목으로 합계 계산")
    void getCart_loggedIn() {
        given(cartItemRepository.findByUserId(USER_ID))
                .willReturn(List.of(cartItem(10L, 1_000L, 2), cartItem(20L, 500L, 3)));

        CartResponse response = cartService.getCart(loggedIn());

        assertThat(response.items()).hasSize(2);
        assertThat(response.totalPrice()).isEqualTo(3_500L);
        assertThat(response.totalCount()).isEqualTo(5);
        then(redisTemplate).should(never()).opsForValue();
    }

    @Test
    @DisplayName("담기 — 신규 상품은 Product Service 조회값으로 저장 (클라이언트 가격 미신뢰)")
    void addItem_loggedIn_new() {
        given(productClient.getProduct(10L)).willReturn(productInfo(10L));
        given(cartItemRepository.findByUserIdAndProductId(USER_ID, 10L)).willReturn(Optional.empty());

        cartService.addItem(loggedIn(), new CartAddRequest(10L, 2));

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        then(cartItemRepository).should().save(captor.capture());
        CartItem saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getProductName()).isEqualTo("테스트 상품");
        assertThat(saved.getPrice()).isEqualTo(1_000L);
        assertThat(saved.getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("담기 — 이미 담긴 상품은 수량만 증가하고 새로 저장하지 않음")
    void addItem_loggedIn_existing() {
        CartItem existing = cartItem(10L, 1_000L, 2);
        given(productClient.getProduct(10L)).willReturn(productInfo(10L));
        given(cartItemRepository.findByUserIdAndProductId(USER_ID, 10L)).willReturn(Optional.of(existing));

        cartService.addItem(loggedIn(), new CartAddRequest(10L, 3));

        assertThat(existing.getQuantity()).isEqualTo(5);
        then(cartItemRepository).should(never()).save(any(CartItem.class));
    }

    @Test
    @DisplayName("담기 — 존재하지 않는 상품이면 예외, 저장하지 않음")
    void addItem_productNotFound() {
        given(productClient.getProduct(99L)).willThrow(new ProductNotFoundException(99L));

        assertThatThrownBy(() -> cartService.addItem(loggedIn(), new CartAddRequest(99L, 1)))
                .isInstanceOf(ProductNotFoundException.class);

        then(cartItemRepository).should(never()).save(any(CartItem.class));
    }

    @Test
    @DisplayName("수량 변경 — 로그인 사용자 항목 수량 갱신")
    void updateItem_loggedIn() {
        CartItem existing = cartItem(10L, 1_000L, 2);
        given(cartItemRepository.findByUserIdAndProductId(USER_ID, 10L)).willReturn(Optional.of(existing));

        cartService.updateItem(loggedIn(), 10L, 7);

        assertThat(existing.getQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("수량 변경 — 장바구니에 없는 상품이면 404 예외")
    void updateItem_loggedIn_notFound() {
        given(cartItemRepository.findByUserIdAndProductId(USER_ID, 99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateItem(loggedIn(), 99L, 1))
                .isInstanceOf(CartItemNotFoundException.class);
    }

    @Test
    @DisplayName("수량 변경 — 하한(1 미만) 거부")
    void updateItem_belowMin() {
        given(cartItemRepository.findByUserIdAndProductId(USER_ID, 10L))
                .willReturn(Optional.of(cartItem(10L, 1_000L, 2)));

        assertThatThrownBy(() -> cartService.updateItem(loggedIn(), 10L, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("수량 변경 — 상한(999 초과) 거부")
    void updateItem_aboveMax() {
        given(cartItemRepository.findByUserIdAndProductId(USER_ID, 10L))
                .willReturn(Optional.of(cartItem(10L, 1_000L, 2)));

        assertThatThrownBy(() -> cartService.updateItem(loggedIn(), 10L, MAX_QUANTITY + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("담기 — 누적 수량이 상한을 넘으면 거부")
    void addItem_exceedsMax() {
        given(productClient.getProduct(10L)).willReturn(productInfo(10L));
        given(cartItemRepository.findByUserIdAndProductId(USER_ID, 10L))
                .willReturn(Optional.of(cartItem(10L, 1_000L, MAX_QUANTITY)));

        assertThatThrownBy(() -> cartService.addItem(loggedIn(), new CartAddRequest(10L, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("개별 삭제 — 요청자 본인 항목만 삭제")
    void removeItem_loggedIn() {
        cartService.removeItem(loggedIn(), 10L);

        then(cartItemRepository).should().deleteByUserIdAndProductId(USER_ID, 10L);
        then(cartItemRepository).should(never()).deleteByUserIdAndProductId(eq(OTHER_USER_ID), anyLong());
    }

    @Test
    @DisplayName("전체 비우기 — 요청자 본인 장바구니만 비움")
    void clearCart_loggedIn() {
        cartService.clearCart(loggedIn());

        then(cartItemRepository).should().deleteByUserId(USER_ID);
        then(cartItemRepository).should(never()).deleteByUserId(OTHER_USER_ID);
        then(redisTemplate).should(never()).delete(anyString());
    }

    @Test
    @DisplayName("격리 — 다른 사용자 조회는 자신의 userId로만 DB 조회")
    void getCart_isolatedPerUser() {
        given(cartItemRepository.findByUserId(OTHER_USER_ID)).willReturn(List.of());

        CartResponse response = cartService.getCart(new CartPrincipal(OTHER_USER_ID, null));

        assertThat(response.items()).isEmpty();
        then(cartItemRepository).should(never()).findByUserId(USER_ID);
    }

    // ── 게스트 사용자 (Redis) ──────────────────────────────────────

    @Test
    @DisplayName("조회 — 게스트는 Redis 키(cart:guest:{guestId})에서 조회")
    void getCart_guest() {
        givenGuestJson(guestJson(10L, "테스트 상품", 1_000L, 2));

        CartResponse response = cartService.getCart(guest());

        assertThat(response.items()).hasSize(1);
        assertThat(response.totalPrice()).isEqualTo(2_000L);
        assertThat(response.totalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("조회 — 식별자 없는 요청(게스트 쿠키 없음)은 빈 장바구니")
    void getCart_noPrincipal() {
        CartResponse response = cartService.getCart(new CartPrincipal(null, null));

        assertThat(response.items()).isEmpty();
        assertThat(response.totalPrice()).isZero();
    }

    @Test
    @DisplayName("담기 — 게스트 신규 상품은 Redis에 TTL과 함께 저장")
    void addItem_guest_new() {
        given(productClient.getProduct(10L)).willReturn(productInfo(10L));
        givenGuestJson(null);

        cartService.addItem(guest(), new CartAddRequest(10L, 2));

        String saved = captureGuestSave();
        assertThat(saved).contains("\"productId\":10", "\"quantity\":2", "\"price\":1000");
        assertThat(saved).doesNotContain("@class");
    }

    @Test
    @DisplayName("담기 — 게스트 기존 상품은 수량 합산")
    void addItem_guest_existing() {
        given(productClient.getProduct(10L)).willReturn(productInfo(10L));
        givenGuestJson(guestJson(10L, "테스트 상품", 1_000L, 2));

        cartService.addItem(guest(), new CartAddRequest(10L, 3));

        assertThat(captureGuestSave()).contains("\"quantity\":5");
    }

    @Test
    @DisplayName("수량 변경 — 게스트 항목 수량 갱신")
    void updateItem_guest() {
        givenGuestJson(guestJson(10L, "테스트 상품", 1_000L, 2));

        cartService.updateItem(guest(), 10L, 4);

        assertThat(captureGuestSave()).contains("\"quantity\":4");
    }

    @Test
    @DisplayName("수량 변경 — 게스트 장바구니에 없는 상품이면 404 예외")
    void updateItem_guest_notFound() {
        givenGuestJson("[]");

        assertThatThrownBy(() -> cartService.updateItem(guest(), 99L, 1))
                .isInstanceOf(CartItemNotFoundException.class);
    }

    @Test
    @DisplayName("개별 삭제 — 게스트 항목 제거 후 나머지 저장")
    void removeItem_guest() {
        givenGuestJson("[" + guestItem(10L, "A", 1_000L, 1) + "," + guestItem(20L, "B", 500L, 1) + "]");

        cartService.removeItem(guest(), 10L);

        String saved = captureGuestSave();
        assertThat(saved).contains("\"productId\":20");
        assertThat(saved).doesNotContain("\"productId\":10");
    }

    @Test
    @DisplayName("전체 비우기 — 게스트는 Redis 키 삭제")
    void clearCart_guest() {
        cartService.clearCart(guest());

        then(redisTemplate).should().delete(GUEST_KEY);
        then(cartItemRepository).should(never()).deleteByUserId(anyLong());
    }

    // ── 게스트 → 로그인 병합 ───────────────────────────────────────

    @Test
    @DisplayName("병합 — 기존 항목은 수량 증가, 신규 항목은 저장 후 게스트 키 삭제")
    void mergeGuestCart() {
        CartItem existing = cartItem(10L, 1_000L, 1);
        givenGuestJson("[" + guestItem(10L, "A", 1_000L, 2) + "," + guestItem(20L, "B", 500L, 3) + "]");
        given(cartItemRepository.findByUserIdAndProductId(USER_ID, 10L)).willReturn(Optional.of(existing));
        given(cartItemRepository.findByUserIdAndProductId(USER_ID, 20L)).willReturn(Optional.empty());

        cartService.mergeGuestCart(USER_ID, GUEST_ID);

        assertThat(existing.getQuantity()).isEqualTo(3);
        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        then(cartItemRepository).should().save(captor.capture());
        assertThat(captor.getValue().getProductId()).isEqualTo(20L);
        then(redisTemplate).should().delete(GUEST_KEY);
    }

    @Test
    @DisplayName("병합 — 게스트 장바구니가 비어 있으면 아무 것도 하지 않음")
    void mergeGuestCart_empty() {
        givenGuestJson(null);

        cartService.mergeGuestCart(USER_ID, GUEST_ID);

        then(cartItemRepository).should(never()).save(any(CartItem.class));
        then(redisTemplate).should(never()).delete(anyString());
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────

    private CartPrincipal loggedIn() {
        return new CartPrincipal(USER_ID, null);
    }

    private CartPrincipal guest() {
        return new CartPrincipal(null, GUEST_ID);
    }

    private CartItem cartItem(Long productId, Long price, int quantity) {
        return CartItem.builder()
                .userId(USER_ID)
                .productId(productId)
                .productName("테스트 상품")
                .price(price)
                .quantity(quantity)
                .build();
    }

    private ProductClient.ProductInfo productInfo(Long productId) {
        return new ProductClient.ProductInfo(productId, "테스트 상품", 1_000L, 10, null, 9L);
    }

    private String guestJson(Long productId, String name, Long price, int quantity) {
        return "[" + guestItem(productId, name, price, quantity) + "]";
    }

    private String guestItem(Long productId, String name, Long price, int quantity) {
        return "{\"productId\":" + productId
                + ",\"productName\":\"" + name + "\""
                + ",\"price\":" + price
                + ",\"quantity\":" + quantity
                + ",\"imageUrl\":null}";
    }

    private void givenGuestJson(String json) {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(GUEST_KEY)).willReturn(json);
    }

    private String captureGuestSave() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        then(valueOperations).should()
                .set(eq(GUEST_KEY), captor.capture(), eq(30L), eq(TimeUnit.DAYS));
        return captor.getValue();
    }
}
