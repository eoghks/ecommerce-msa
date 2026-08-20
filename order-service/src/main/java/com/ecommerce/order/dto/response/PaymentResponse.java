package com.ecommerce.order.dto.response;

import com.ecommerce.order.domain.Payment;
import com.ecommerce.order.domain.PaymentProvider;
import com.ecommerce.order.domain.PaymentStatus;

import java.time.LocalDateTime;

/** 결제 조회 응답 — PG 거래키는 노출하지 않는다(취소는 서버가 수행) */
public record PaymentResponse(
        Long id,
        Long orderId,
        PaymentProvider pgProvider,
        PaymentStatus status,
        Long amount,
        Long cancelledAmount,
        LocalDateTime approvedAt,
        LocalDateTime canceledAt,
        String failReason
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getPgProvider(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getCancelledAmount(),
                payment.getApprovedAt(),
                payment.getCanceledAt(),
                payment.getFailReason()
        );
    }
}
