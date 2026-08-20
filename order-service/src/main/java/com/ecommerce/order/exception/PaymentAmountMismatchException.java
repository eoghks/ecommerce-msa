package com.ecommerce.order.exception;

/**
 * 승인 요청 금액이 서버가 계산한 결제금액과 다름 — 위변조 차단(§8).
 * 실제 금액은 메시지에 담지 않는다(대조 시도를 돕지 않기 위해).
 */
public class PaymentAmountMismatchException extends RuntimeException {

    public PaymentAmountMismatchException(Long orderId) {
        super("결제 요청 금액이 주문 금액과 일치하지 않습니다. orderId=" + orderId);
    }
}
