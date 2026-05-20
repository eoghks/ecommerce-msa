package com.ecommerce.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record OrderCreateRequest(

        @NotEmpty(message = "주문 상품은 1개 이상이어야 합니다.")
        @Valid
        List<OrderItemRequest> items
) {}
