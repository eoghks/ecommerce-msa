package com.ecommerce.auth.dto;

import com.ecommerce.auth.domain.User;

/**
 * 서비스 간 사용자 요약 조회용 DTO.
 * product-service가 상품의 판매자 정보를 표시할 때 사용.
 */
public record UserSummaryResponse(
        Long id,
        String name,
        String email
) {
    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(user.getId(), user.getName(), user.getEmail());
    }
}
