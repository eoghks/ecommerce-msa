package com.ecommerce.order.dto.request;

import jakarta.validation.constraints.Min;

public record CartUpdateRequest(@Min(1) int quantity) {}
