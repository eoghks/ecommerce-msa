package com.ecommerce.order.exception;

/**
 * M-03: 같은 주문에 진행 중(READY)·미확정(UNKNOWN) 결제가 있어 새 승인을 받지 않는다.
 * 이중 승인(이중 출금)을 애플리케이션 단계에서 먼저 차단하고, DB 부분 유니크 인덱스는 최종 방어선으로 남긴다.
 */
public class PaymentInProgressException extends RuntimeException {

    public PaymentInProgressException(Long orderId) {
        super("이미 진행 중인 결제가 있습니다. 잠시 후 결제 상태를 확인해주세요. orderId=" + orderId);
    }
}
