package com.ecommerce.product.exception;

/** 검색 파라미터가 유효하지 않을 때 (가격대 음수·역전 등) */
public class InvalidSearchParameterException extends RuntimeException {

    public InvalidSearchParameterException(String message) {
        super(message);
    }
}
