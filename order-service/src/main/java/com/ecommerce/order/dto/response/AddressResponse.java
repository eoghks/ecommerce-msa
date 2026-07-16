package com.ecommerce.order.dto.response;

import com.ecommerce.order.domain.Address;

import java.time.LocalDateTime;

public record AddressResponse(
        Long id,
        String receiver,
        String phone,
        String address,
        boolean isDefault,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AddressResponse from(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getReceiver(),
                address.getPhone(),
                address.getAddress(),
                address.isDefault(),
                address.getCreatedAt(),
                address.getUpdatedAt()
        );
    }
}
