package com.ecommerce.order.dto.request;

import com.ecommerce.order.domain.DeliveryStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 배송상태 변경 요청 — 전이시킬 다음 상태.
 */
public record DeliveryStatusUpdateRequest(

        @NotNull(message = "변경할 배송상태는 필수입니다.")
        DeliveryStatus status
) {}
