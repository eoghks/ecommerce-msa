package com.ecommerce.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 결제 승인 요청 — 프론트 결제위젯 성공 리다이렉트 값 그대로 전달한다.
 * amount 는 참고용 대조값이며, 실제 승인 금액은 서버가 주문에서 계산한 payableAmount 를 쓴다(§8).
 * M-05: pgOrderId 는 시도마다 달라지므로 서버가 재생성할 수 없다 — 규칙·주문 id 일치를 검증한 뒤 저장해
 *       승인·재조회에 사용한다(PgOrderId.requireForOrder).
 */
public record PaymentConfirmRequest(

        @NotBlank(message = "결제 키는 필수입니다.")
        @Size(max = 200, message = "결제 키가 올바르지 않습니다.")
        String paymentKey,

        @NotBlank(message = "PG 주문번호는 필수입니다.")
        @Size(max = 64, message = "PG 주문번호가 올바르지 않습니다.")
        String pgOrderId,

        @NotNull(message = "주문 번호는 필수입니다.")
        Long orderId,

        @NotNull(message = "결제 금액은 필수입니다.")
        @Positive(message = "결제 금액은 0보다 커야 합니다.")
        Long amount
) {}
