package com.ecommerce.order.exception;

/**
 * H-03/M-01: PG 응답이 승인 완료(status=DONE)가 아니거나 우리 요청과 대조되지 않는 상태.
 * 가상계좌 입금대기(WAITING_FOR_DEPOSIT)·승인 진행 중(IN_PROGRESS)·금액/주문번호 불일치가 여기에 해당한다.
 * 이 응답으로 결제를 확정하면 미입금 주문이 재고 차감·배송까지 진행되므로 확정하지 않고 보상 취소한다.
 */
public class PaymentNotCompletedException extends RuntimeException {

    public PaymentNotCompletedException(String message) {
        super(message);
    }
}
