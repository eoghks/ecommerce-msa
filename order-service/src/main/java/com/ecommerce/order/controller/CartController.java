package com.ecommerce.order.controller;

import com.ecommerce.order.dto.request.CartAddRequest;
import com.ecommerce.order.dto.request.CartUpdateRequest;
import com.ecommerce.order.dto.response.CartResponse;
import com.ecommerce.order.service.CartService;
import com.ecommerce.order.support.CartPrincipal;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartResponse> getCart(CartPrincipal principal) {
        return ResponseEntity.ok(cartService.getCart(principal));
    }

    @PostMapping("/items")
    public ResponseEntity<Void> addItem(CartPrincipal principal,
                                        @Valid @RequestBody CartAddRequest request) {
        cartService.addItem(principal, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PatchMapping("/items/{productId}")
    public ResponseEntity<Void> updateItem(CartPrincipal principal,
                                           @PathVariable Long productId,
                                           @Valid @RequestBody CartUpdateRequest request) {
        cartService.updateItem(principal, productId, request.quantity());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Void> removeItem(CartPrincipal principal,
                                           @PathVariable Long productId) {
        cartService.removeItem(principal, productId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(CartPrincipal principal) {
        cartService.clearCart(principal);
        return ResponseEntity.noContent().build();
    }

    /**
     * 로그인 후 게스트 장바구니 → 사용자 장바구니 병합.
     * CR-04: 비로그인 요청은 401 반환 — /merge는 인증 필수
     * HR-01: 병합 성공 후 guestId HttpOnly 쿠키 만료 처리 (서버에서만 가능)
     */
    @PostMapping("/merge")
    public ResponseEntity<Void> mergeGuestCart(CartPrincipal principal,
                                               HttpServletResponse response) {
        if (!principal.isLoggedIn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (principal.guestId() != null) {
            cartService.mergeGuestCart(principal.userId(), principal.guestId());
            // 병합 완료 후 guestId 쿠키 삭제 (Max-Age=0)
            response.setHeader("Set-Cookie",
                    "guestId=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax");
        }
        return ResponseEntity.ok().build();
    }

    /**
     * 게스트 식별자 초기화.
     * CR-06: XSS로부터 guestId 보호 — 서버가 HttpOnly 쿠키 발급.
     * 쿠키가 이미 있으면 재발급 없이 그대로 유지.
     */
    @PostMapping("/guest/init")
    public ResponseEntity<Void> initGuestId(HttpServletRequest request,
                                            HttpServletResponse response) {
        boolean alreadySet = Optional.ofNullable(request.getCookies())
                .map(Arrays::stream)
                .flatMap(s -> s.filter(c -> "guestId".equals(c.getName())).findFirst())
                .isPresent();

        if (!alreadySet) {
            Cookie cookie = new Cookie("guestId", UUID.randomUUID().toString());
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge(30 * 24 * 60 * 60); // 30일
            // SameSite=Lax — CSRF 방어 (크로스 사이트 POST 차단)
            response.setHeader("Set-Cookie",
                    String.format("guestId=%s; Max-Age=%d; Path=/; HttpOnly; SameSite=Lax",
                            cookie.getValue(), cookie.getMaxAge()));
        }
        return ResponseEntity.ok().build();
    }
}
