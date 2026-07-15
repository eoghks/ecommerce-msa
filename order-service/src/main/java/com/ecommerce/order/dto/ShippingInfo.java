package com.ecommerce.order.dto;

/**
 * 주문 배송지 스냅샷 값. 주소록 선택(addressId) 또는 직접 입력 결과를 담아
 * 주문 저장 시 receiver/phone/address 컬럼으로 복사한다.
 */
public record ShippingInfo(
        String receiver,
        String phone,
        String address
) {}
