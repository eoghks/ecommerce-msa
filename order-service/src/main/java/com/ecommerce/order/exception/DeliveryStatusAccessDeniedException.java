package com.ecommerce.order.exception;

/** 배송상태를 변경할 권한이 없는 사용자(본인 상품이 없는 SELLER 등) → 403 */
public class DeliveryStatusAccessDeniedException extends RuntimeException {
    public DeliveryStatusAccessDeniedException(Long orderId) {
        super("배송상태를 변경할 권한이 없습니다. orderId=" + orderId);
    }
}
