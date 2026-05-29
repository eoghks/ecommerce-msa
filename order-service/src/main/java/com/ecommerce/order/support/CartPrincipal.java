package com.ecommerce.order.support;

/**
 * Cart API 전용 요청자 식별 객체.
 * - 로그인: Gateway가 주입한 X-User-Id 헤더
 * - 비로그인: 브라우저 쿠키의 guestId (UUID, 30일 TTL)
 */
public record CartPrincipal(Long userId, String guestId) {

    public boolean isLoggedIn() {
        return userId != null;
    }
}
