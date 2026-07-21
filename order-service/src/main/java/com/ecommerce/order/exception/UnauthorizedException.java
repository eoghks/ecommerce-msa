package com.ecommerce.order.exception;

/** 인증 정보(X-User-Id) 부재 시 → 401 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
