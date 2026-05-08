package com.ecommerce.auth.dto;

import lombok.Getter;

@Getter
public class RefreshResponse {

    private final String accessToken;
    private final String refreshToken;
    private final String tokenType = "Bearer";
    private final long   expiresIn;

    public RefreshResponse(String accessToken, String refreshToken, long expiresIn) {
        this.accessToken  = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn    = expiresIn;
    }
}
