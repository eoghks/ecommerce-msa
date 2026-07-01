package com.ecommerce.auth.dto;

import lombok.Getter;

@Getter
public class ForgotPasswordResponse {

    private final String message;
    // dev 환경에서만 반환 (prod: null)
    private final String tempPassword;

    public ForgotPasswordResponse(String message, String tempPassword) {
        this.message      = message;
        this.tempPassword = tempPassword;
    }
}
