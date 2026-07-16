package com.ecommerce.order.controller;

import com.ecommerce.order.dto.request.AddressRequest;
import com.ecommerce.order.dto.response.AddressResponse;
import com.ecommerce.order.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    /** 내 주소록 목록 (기본 배송지 우선) */
    @GetMapping
    public ResponseEntity<List<AddressResponse>> getMyAddresses(
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        return ResponseEntity.ok(addressService.getMyAddresses(userId));
    }

    /** 배송지 추가 (첫 주소는 자동 기본) */
    @PostMapping
    public ResponseEntity<AddressResponse> addAddress(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody AddressRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(addressService.addAddress(userId, request));
    }

    /** 배송지 수정 (본인) */
    @PutMapping("/{addressId}")
    public ResponseEntity<AddressResponse> updateAddress(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long addressId,
            @Valid @RequestBody AddressRequest request
    ) {
        return ResponseEntity.ok(addressService.updateAddress(userId, addressId, request));
    }

    /** 배송지 삭제 (본인) */
    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> deleteAddress(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long addressId
    ) {
        addressService.deleteAddress(userId, addressId);
        return ResponseEntity.noContent().build();
    }

    /** 기본 배송지 지정 (기존 기본 해제) */
    @PatchMapping("/{addressId}/default")
    public ResponseEntity<AddressResponse> setDefault(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long addressId
    ) {
        return ResponseEntity.ok(addressService.setDefault(userId, addressId));
    }
}
