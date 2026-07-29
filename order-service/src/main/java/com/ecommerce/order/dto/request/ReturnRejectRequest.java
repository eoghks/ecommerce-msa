package com.ecommerce.order.dto.request;

import com.ecommerce.order.domain.ReturnRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 반품 거부 요청 (V1.1-5) — 거부 사유 필수 */
public record ReturnRejectRequest(

        @NotBlank(message = "거부 사유를 입력해주세요.")
        @Size(max = ReturnRequest.MAX_REASON_LENGTH, message = "거부 사유는 300자 이하여야 합니다.")
        String rejectReason
) {}
