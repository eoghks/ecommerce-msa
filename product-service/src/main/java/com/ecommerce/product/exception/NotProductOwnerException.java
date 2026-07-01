package com.ecommerce.product.exception;

public class NotProductOwnerException extends RuntimeException {
    public NotProductOwnerException(Long productId) {
        super("본인이 등록한 상품만 수정/삭제할 수 있습니다. productId=" + productId);
    }
}
