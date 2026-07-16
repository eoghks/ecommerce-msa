package com.ecommerce.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 배송지 추가/수정 요청.
 */
public record AddressRequest(

        @NotBlank(message = "수령인을 입력해주세요.")
        @Size(max = 100)
        String receiver,

        @NotBlank(message = "연락처를 입력해주세요.")
        @Size(max = 20)
        @Pattern(regexp = "^0\\d{1,2}-?\\d{3,4}-?\\d{4}$", message = "올바른 전화번호 형식이 아닙니다.")
        String phone,

        @NotBlank(message = "배송지를 입력해주세요.")
        @Size(max = 300)
        String address
) {}
