package com.ecommerce.auth.dto;

public record SellerApplyResponse(
        String message,
        String accessToken,
        long expiresIn
) {}
