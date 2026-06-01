package com.ecommerce.auth.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Refresh Token을 HttpOnly 쿠키로 발급/만료 처리.
 *
 * - HttpOnly: JS 접근 불가 → XSS로 토큰 탈취 방지
 * - SameSite=Lax: CSRF 방어 (크로스 사이트 POST 차단)
 * - Secure: 운영(HTTPS)에서만 true — app.cookie.secure 프로퍼티로 제어
 *   (개발/사내 IP는 HTTP라 false여야 쿠키가 전송됨)
 */
@Component
public class RefreshTokenCookie {

    public static final String NAME = "refreshToken";

    @Value("${app.cookie.secure:false}")
    private boolean secure;

    @Value("${jwt.refresh-token-expiry-ms:604800000}")
    private long refreshTokenExpiryMs;

    /** 로그인/갱신 시 발급 */
    public String create(String token) {
        return ResponseCookie.from(NAME, token)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(Duration.ofMillis(refreshTokenExpiryMs))
                .sameSite("Lax")
                .build()
                .toString();
    }

    /** 로그아웃 시 만료 (Max-Age=0) */
    public String expire() {
        return ResponseCookie.from(NAME, "")
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build()
                .toString();
    }
}
