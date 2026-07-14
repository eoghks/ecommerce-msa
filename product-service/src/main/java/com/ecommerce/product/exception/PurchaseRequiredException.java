package com.ecommerce.product.exception;

public class PurchaseRequiredException extends RuntimeException {

    public PurchaseRequiredException(Long productId) {
        super("구매한 상품에만 리뷰를 작성할 수 있습니다. productId=" + productId);
    }
}
