package com.ecommerce.product.exception;

public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException() {
        super("인증이 필요합니다.");
    }
}
