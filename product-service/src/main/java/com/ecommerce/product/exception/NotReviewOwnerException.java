package com.ecommerce.product.exception;

public class NotReviewOwnerException extends RuntimeException {

    public NotReviewOwnerException(Long reviewId) {
        super("본인이 작성한 리뷰만 수정/삭제할 수 있습니다. reviewId=" + reviewId);
    }
}
