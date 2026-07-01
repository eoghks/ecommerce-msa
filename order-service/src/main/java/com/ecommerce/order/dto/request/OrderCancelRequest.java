package com.ecommerce.order.dto.request;

import jakarta.validation.constraints.Size;

/**
 * 사용자 주문 취소 요청 (M-N3) — 사유는 선택.
 * 미입력 시 서비스에서 기본 사유("고객 주문 취소")로 대체.
 */
public record OrderCancelRequest(

        @Size(max = 300, message = "취소 사유는 300자 이하여야 합니다.")
        String reason
) {}
