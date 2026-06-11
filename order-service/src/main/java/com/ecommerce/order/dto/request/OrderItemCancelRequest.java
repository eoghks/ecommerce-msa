package com.ecommerce.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrderItemCancelRequest(

        @NotBlank(message = "취소 사유를 입력해주세요.")
        @Size(max = 300, message = "취소 사유는 300자 이하여야 합니다.")
        String reason
) {}
