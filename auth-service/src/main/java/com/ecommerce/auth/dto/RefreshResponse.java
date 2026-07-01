package com.ecommerce.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;

@Getter
public class RefreshResponse {

    private final String accessToken;
    // refreshToken은 HttpOnly 쿠키로 전달 — 응답 바디에 노출하지 않음 (XSS 방어)
    @JsonIgnore
    private final String refreshToken;
    private final String tokenType = "Bearer";
    private final long   expiresIn;

    public RefreshResponse(String accessToken, String refreshToken, long expiresIn) {
        this.accessToken  = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn    = expiresIn;
    }
}
