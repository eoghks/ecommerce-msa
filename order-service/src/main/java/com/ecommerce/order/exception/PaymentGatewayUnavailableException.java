package com.ecommerce.order.exception;

/**
 * C-01: PG 통신 실패 — 응답을 받지 못해 승인 결과가 확정되지 않은 상태.
 * 명시적 거절(PaymentApprovalFailedException)과 반드시 구분한다 — 타임아웃 시점에 토스는 이미
 * 승인(출금)했을 수 있으므로 확정 실패로 단정하면 환불 경로가 사라진다.
 * M-04: 클라이언트·모니터링이 재시도 가능 여부를 판단할 수 있게 502 로 응답한다.
 */
public class PaymentGatewayUnavailableException extends RuntimeException {

    public PaymentGatewayUnavailableException(String message) {
        super(message);
    }
}
