package com.ecommerce.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrderCreateRequest(

        @NotEmpty(message = "주문 상품은 1개 이상이어야 합니다.")
        @Valid
        List<OrderItemRequest> items,

        // HR-05: 배송 정보 필수 입력
        @NotBlank(message = "수령인을 입력해주세요.")
        @Size(max = 100)
        String receiver,

        @NotBlank(message = "연락처를 입력해주세요.")
        @Size(max = 20)
        String phone,

        @NotBlank(message = "배송지를 입력해주세요.")
        @Size(max = 300)
        String address
) {}
