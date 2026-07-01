package com.ecommerce.order.service;

import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.domain.CartItem;
import com.ecommerce.order.domain.CartItemRecord;
import com.ecommerce.order.dto.request.CartAddRequest;
import com.ecommerce.order.dto.response.CartItemResponse;
import com.ecommerce.order.dto.response.CartResponse;
import com.ecommerce.order.exception.CartItemNotFoundException;
import com.ecommerce.order.repository.CartItemRepository;
import com.ecommerce.order.support.CartPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private static final String GUEST_CART_PREFIX = "cart:guest:";
    private static final long   GUEST_TTL_DAYS    = 30;

    private final CartItemRepository cartItemRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper redisObjectMapper;
    private final ProductClient productClient;

    // ── 조회 ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CartResponse getCart(CartPrincipal principal) {
        List<CartItemResponse> items = principal.isLoggedIn()
                ? getDbItems(principal.userId())
                : getGuestItems(principal.guestId());
        return CartResponse.of(items);
    }

    // ── 추가 ─────────────────────────────────────────────────────────────────

    @Transactional
    public void addItem(CartPrincipal principal, CartAddRequest req) {
        if (principal.isLoggedIn()) {
            addDbItem(principal.userId(), req);
        } else {
            addGuestItem(principal.guestId(), req);
        }
    }

    // ── 수량 수정 ─────────────────────────────────────────────────────────────

    @Transactional
    public void updateItem(CartPrincipal principal, Long productId, int quantity) {
        if (principal.isLoggedIn()) {
            // HR-03: 존재하지 않는 항목 수정 시 404 반환
            CartItem item = cartItemRepository.findByUserIdAndProductId(principal.userId(), productId)
                    .orElseThrow(() -> new CartItemNotFoundException(productId));
            item.updateQuantity(quantity);
        } else {
            List<CartItemRecord> items = readGuestItems(principal.guestId());
            boolean found = items.stream().anyMatch(i -> i.productId().equals(productId));
            if (!found) throw new CartItemNotFoundException(productId);
            List<CartItemRecord> updated = items.stream()
                    .map(i -> i.productId().equals(productId) ? i.withQuantity(quantity) : i)
                    .toList();
            saveGuestItems(principal.guestId(), updated);
        }
    }

    // ── 개별 삭제 ─────────────────────────────────────────────────────────────

    @Transactional
    public void removeItem(CartPrincipal principal, Long productId) {
        if (principal.isLoggedIn()) {
            cartItemRepository.deleteByUserIdAndProductId(principal.userId(), productId);
        } else {
            List<CartItemRecord> items = readGuestItems(principal.guestId());
            saveGuestItems(principal.guestId(), items.stream()
                    .filter(i -> !i.productId().equals(productId))
                    .toList());
        }
    }

    // ── 전체 비우기 ───────────────────────────────────────────────────────────

    @Transactional
    public void clearCart(CartPrincipal principal) {
        if (principal.isLoggedIn()) {
            cartItemRepository.deleteByUserId(principal.userId());
        } else {
            redisTemplate.delete(guestKey(principal.guestId()));
        }
    }

    // ── 로그인 시 게스트 → DB 병합 ────────────────────────────────────────────

    @Transactional
    public void mergeGuestCart(Long userId, String guestId) {
        List<CartItemRecord> guestItems = readGuestItems(guestId);
        if (guestItems.isEmpty()) return;

        for (CartItemRecord record : guestItems) {
            cartItemRepository.findByUserIdAndProductId(userId, record.productId())
                    .ifPresentOrElse(
                            existing -> existing.addQuantity(record.quantity()),
                            () -> cartItemRepository.save(CartItem.builder()
                                    .userId(userId)
                                    .productId(record.productId())
                                    .productName(record.productName())
                                    .price(record.price())
                                    .quantity(record.quantity())
                                    .imageUrl(record.imageUrl())
                                    .build())
                    );
        }

        // 병합 완료 후 게스트 키 삭제
        redisTemplate.delete(guestKey(guestId));
        log.info("게스트 장바구니 병합 완료: guestId={}, userId={}, itemCount={}", guestId, userId, guestItems.size());
    }

    // ── DB 헬퍼 ──────────────────────────────────────────────────────────────

    private List<CartItemResponse> getDbItems(Long userId) {
        return cartItemRepository.findByUserId(userId).stream()
                .map(CartItemResponse::from)
                .toList();
    }

    private void addDbItem(Long userId, CartAddRequest req) {
        // CR-01: 가격·상품명을 클라이언트 입력 대신 Product Service에서 서버 측 조회
        ProductClient.ProductInfo product = productClient.getProduct(req.productId());
        cartItemRepository.findByUserIdAndProductId(userId, req.productId())
                .ifPresentOrElse(
                        existing -> existing.addQuantity(req.quantity()),
                        () -> cartItemRepository.save(CartItem.builder()
                                .userId(userId)
                                .productId(product.id())
                                .productName(product.name())
                                .price(product.price())
                                .quantity(req.quantity())
                                .imageUrl(product.imageUrl())
                                .build())
                );
    }

    // ── Redis 헬퍼 ────────────────────────────────────────────────────────────

    private List<CartItemResponse> getGuestItems(String guestId) {
        return readGuestItems(guestId).stream()
                .map(CartItemResponse::from)
                .toList();
    }

    private void addGuestItem(String guestId, CartAddRequest req) {
        // CR-01: 가격·상품명을 클라이언트 입력 대신 Product Service에서 서버 측 조회
        ProductClient.ProductInfo product = productClient.getProduct(req.productId());
        List<CartItemRecord> items = new ArrayList<>(readGuestItems(guestId));
        boolean exists = false;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).productId().equals(req.productId())) {
                items.set(i, items.get(i).addQuantity(req.quantity()));
                exists = true;
                break;
            }
        }
        if (!exists) {
            items.add(new CartItemRecord(product.id(), product.name(), product.price(), req.quantity(), product.imageUrl()));
        }
        saveGuestItems(guestId, items);
    }

    private List<CartItemRecord> readGuestItems(String guestId) {
        if (guestId == null) return List.of();
        try {
            String json = redisTemplate.opsForValue().get(guestKey(guestId));
            if (json == null) return new ArrayList<>();
            return redisObjectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("게스트 장바구니 Redis 읽기 실패: guestId={}, error={}", guestId, e.getMessage());
            return new ArrayList<>();
        }
    }

    private void saveGuestItems(String guestId, List<CartItemRecord> items) {
        try {
            String json = redisObjectMapper.writeValueAsString(items);
            redisTemplate.opsForValue().set(guestKey(guestId), json, GUEST_TTL_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("게스트 장바구니 Redis 저장 실패: guestId={}, error={}", guestId, e.getMessage());
        }
    }

    private String guestKey(String guestId) {
        return GUEST_CART_PREFIX + guestId;
    }


}
