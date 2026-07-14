package com.ecommerce.product.exception;

public class DuplicateReviewException extends RuntimeException {

    public DuplicateReviewException(Long productId) {
        super("이미 이 상품에 리뷰를 작성했습니다. productId=" + productId);
    }
}
