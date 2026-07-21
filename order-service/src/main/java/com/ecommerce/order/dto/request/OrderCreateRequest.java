package com.ecommerce.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrderCreateRequest(

        @NotEmpty(message = "주문 상품은 1개 이상이어야 합니다.")
        @Valid
        List<OrderItemRequest> items,

        // V1.1-3: 선택적 저장형 배송지 선택. 지정 시 해당 주소 값을 스냅샷으로 복사한다.
        Long addressId,

        // HR-05: 직접 입력 배송 정보 (addressId 미지정 시 사용 — 하위호환).
        //        addressId/직접입력 중 최소 하나가 유효해야 하며, 검증은 서비스에서 수행.
        @Size(max = 100)
        String receiver,

        // addressId 사용 시엔 미전송(null)이라 @Pattern 을 통과. 직접 입력 시에만 형식 검증.
        @Size(max = 20)
        @Pattern(regexp = "^0\\d{1,2}-?\\d{3,4}-?\\d{4}$", message = "올바른 전화번호 형식이 아닙니다.")
        String phone,

        @Size(max = 300)
        String address
) {}
