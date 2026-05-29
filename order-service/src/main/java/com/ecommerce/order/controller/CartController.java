package com.ecommerce.order.controller;

import com.ecommerce.order.dto.request.CartAddRequest;
import com.ecommerce.order.dto.request.CartUpdateRequest;
import com.ecommerce.order.dto.response.CartResponse;
import com.ecommerce.order.service.CartService;
import com.ecommerce.order.support.CartPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    /** 로그인 후 게스트 장바구니 → 사용자 장바구니 병합 */
    @PostMapping("/merge")
    public ResponseEntity<Void> mergeGuestCart(CartPrincipal principal) {
        if (principal.guestId() != null && principal.isLoggedIn()) {
            cartService.mergeGuestCart(principal.userId(), principal.guestId());
        }
        return ResponseEntity.ok().build();
    }
}
